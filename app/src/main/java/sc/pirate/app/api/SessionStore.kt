package sc.pirate.app.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.security.AndroidKeystoreSessionCipher
import sc.pirate.app.security.SessionCipher

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "pirate_session")

private val KEY_LEGACY_SESSION = stringPreferencesKey("session_json")
private val KEY_ENCRYPTED_SESSION = stringPreferencesKey("session_encrypted_v1")

class SessionStore internal constructor(
    private val context: Context,
    private val cipher: SessionCipher,
) {
    constructor(context: Context) : this(context, AndroidKeystoreSessionCipher())

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cachedSession: SessionExchangeResponse? = null
    @Volatile private var cacheLoaded: Boolean = false

    suspend fun get(): SessionExchangeResponse? {
        if (cacheLoaded) {
            val cached = cachedSession
            if (cached != null && SessionExpiry.isExpired(cached.accessToken)) {
                clear()
                return null
            }
            return cached
        }

        val prefs = context.sessionDataStore.data.first()
        val encrypted = prefs[KEY_ENCRYPTED_SESSION]
        val legacy = prefs[KEY_LEGACY_SESSION]
        val session = decodeSession(encrypted, legacy)
        if (encrypted != null && session == null) {
            clear()
            return null
        }
        if (encrypted == null && legacy != null && session != null) {
            persistEncrypted(session)
        }
        if (session != null && SessionExpiry.isExpired(session.accessToken)) {
            clear()
            return null
        }
        cachedSession = session
        cacheLoaded = true
        return session
    }

    fun observe(): Flow<SessionExchangeResponse?> =
        context.sessionDataStore.data.map { prefs ->
            val session = decodeSession(
                prefs[KEY_ENCRYPTED_SESSION],
                prefs[KEY_LEGACY_SESSION],
            )
            session?.takeUnless { SessionExpiry.isExpired(it.accessToken) }
        }.onEach {
            cachedSession = it
            cacheLoaded = true
        }

    suspend fun set(session: SessionExchangeResponse) {
        persistEncrypted(session)
        cachedSession = session
        cacheLoaded = true
    }

    private suspend fun persistEncrypted(session: SessionExchangeResponse) {
        val plaintext = json.encodeToString(SessionExchangeResponse.serializer(), session)
        val encrypted = cipher.encrypt(plaintext)
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_SESSION] = encrypted
            prefs.remove(KEY_LEGACY_SESSION)
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(KEY_ENCRYPTED_SESSION)
            prefs.remove(KEY_LEGACY_SESSION)
        }
        cachedSession = null
        cacheLoaded = true
    }

    suspend fun getAccessToken(): String? = get()?.accessToken

    private fun decodeSession(encrypted: String?, legacy: String?): SessionExchangeResponse? = try {
        val raw = when {
            encrypted != null -> cipher.decrypt(encrypted)
            legacy != null -> legacy
            else -> return null
        }
        json.decodeFromString<SessionExchangeResponse>(raw)
    } catch (_: Exception) {
        null
    }
}
