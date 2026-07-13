package sc.pirate.app.legal

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sc.pirate.app.BuildConfig
import sc.pirate.app.api.SessionStore
import sc.pirate.app.security.AndroidKeystoreSessionCipher
import sc.pirate.app.security.SessionCipher

const val CURRENT_TERMS_VERSION = "2026-03-23"

private val Context.termsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pirate_terms_acceptance")
private val KEY_ENCRYPTED_ACCEPTANCE = stringPreferencesKey("terms_acceptance_encrypted_v1")

@Serializable
data class TermsAcceptance(
    val version: String,
    val acceptedAtEpochMs: Long,
)

@Serializable
internal data class TermsAcceptanceLedger(
    val accounts: Map<String, TermsAcceptance> = emptyMap(),
) {
    fun acceptanceFor(userId: String): TermsAcceptance? = accounts[normalizeUserId(userId)]

    fun accepts(userId: String, version: String): Boolean =
        acceptanceFor(userId)?.version == version

    fun withAcceptance(userId: String, acceptance: TermsAcceptance): TermsAcceptanceLedger {
        val account = normalizeUserId(userId)
        require(account.isNotBlank()) { "Sign in before accepting the Terms." }
        require(acceptance.version.isNotBlank()) { "Terms version is unavailable." }
        return copy(accounts = accounts + (account to acceptance))
    }
}

class TermsAcceptanceStore internal constructor(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val cipher: SessionCipher,
    private val nowEpochMs: () -> Long,
) {
    constructor(context: Context, sessionStore: SessionStore) : this(
        context = context,
        sessionStore = sessionStore,
        cipher = AndroidKeystoreSessionCipher(keyAlias = "pirate_terms_acceptance_v1"),
        nowEpochMs = System::currentTimeMillis,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun currentUserId(): String? =
        sessionStore.get()?.user?.userId?.trim()?.takeIf(String::isNotBlank)

    suspend fun currentAcceptance(): TermsAcceptance? {
        val userId = currentUserId() ?: return null
        val ledger = decode(context.termsDataStore.data.first()[KEY_ENCRYPTED_ACCEPTANCE])
        return ledger.acceptanceFor(userId)
    }

    suspend fun hasAcceptedCurrent(): Boolean =
        currentAcceptance()?.version == CURRENT_TERMS_VERSION

    suspend fun acceptCurrent(expectedUserId: String) {
        val userId = currentUserId()
            ?: throw IllegalStateException("Sign in before accepting the Terms.")
        check(userId.equals(expectedUserId, ignoreCase = true)) {
            "Your signed-in account changed. Try the action again."
        }
        context.termsDataStore.edit { preferences ->
            val next = decode(preferences[KEY_ENCRYPTED_ACCEPTANCE]).withAcceptance(
                userId = userId,
                acceptance = TermsAcceptance(
                    version = CURRENT_TERMS_VERSION,
                    acceptedAtEpochMs = nowEpochMs(),
                ),
            )
            preferences[KEY_ENCRYPTED_ACCEPTANCE] = cipher.encrypt(
                json.encodeToString(TermsAcceptanceLedger.serializer(), next),
            )
        }
    }

    private fun decode(encrypted: String?): TermsAcceptanceLedger {
        if (encrypted == null) return TermsAcceptanceLedger()
        return runCatching {
            json.decodeFromString(TermsAcceptanceLedger.serializer(), cipher.decrypt(encrypted))
        }.getOrDefault(TermsAcceptanceLedger())
    }
}

data class TermsPromptState(
    val viewerUserId: String,
    val version: String = CURRENT_TERMS_VERSION,
    val termsUrl: String = "${BuildConfig.WEB_BASE_URL.trimEnd('/')}/terms",
    val privacyUrl: String = "${BuildConfig.WEB_BASE_URL.trimEnd('/')}/privacy",
    val accepting: Boolean = false,
    val error: String? = null,
)

class TermsAcceptanceManager(private val store: TermsAcceptanceStore) {
    private val mutex = Mutex()
    private var pendingResult: CompletableDeferred<Boolean>? = null
    private val _prompt = MutableStateFlow<TermsPromptState?>(null)
    val prompt: StateFlow<TermsPromptState?> = _prompt.asStateFlow()

    suspend fun requireForUgc(): Boolean {
        if (store.hasAcceptedCurrent()) return true
        val viewerUserId = store.currentUserId() ?: return false
        val result = mutex.withLock {
            pendingResult?.takeIf { it.isActive } ?: CompletableDeferred<Boolean>().also {
                pendingResult = it
                _prompt.value = TermsPromptState(viewerUserId = viewerUserId)
            }
        }
        return result.await()
    }

    suspend fun accept() {
        _prompt.value = _prompt.value?.copy(accepting = true, error = null)
        try {
            val viewerUserId = requireNotNull(_prompt.value?.viewerUserId)
            store.acceptCurrent(expectedUserId = viewerUserId)
            complete(true)
        } catch (error: Exception) {
            _prompt.value = _prompt.value?.copy(
                accepting = false,
                error = error.message ?: "Could not save your acceptance.",
            )
        }
    }

    suspend fun dismiss() {
        complete(false)
    }

    private suspend fun complete(accepted: Boolean) {
        mutex.withLock {
            pendingResult?.complete(accepted)
            pendingResult = null
            _prompt.value = null
        }
    }
}

private fun normalizeUserId(userId: String): String = userId.trim().lowercase()
