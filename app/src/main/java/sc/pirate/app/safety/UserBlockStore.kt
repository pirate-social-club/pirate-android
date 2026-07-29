package sc.pirate.app.safety

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sc.pirate.app.api.SessionStore
import sc.pirate.app.security.AndroidKeystoreSessionCipher
import sc.pirate.app.security.SessionCipher

private val Context.userBlockDataStore: DataStore<Preferences> by preferencesDataStore(name = "pirate_user_blocks")
private val KEY_ENCRYPTED_BLOCKS = stringPreferencesKey("user_blocks_encrypted_v1")

@Serializable
data class BlockedUser(
    val userId: String,
    val handleLabel: String? = null,
    val xmtpInbox: String? = null,
    val blockedAtEpochMs: Long,
)

data class UserBlockState(
    val viewerUserId: String? = null,
    val users: List<BlockedUser> = emptyList(),
) {
    val userIds: Set<String> = users.mapTo(linkedSetOf()) { normalizePirateUserId(it.userId) }
    val xmtpInboxes: Set<String> = users.mapNotNullTo(linkedSetOf()) {
        it.xmtpInbox?.let(::normalizeXmtpInbox)?.takeIf(String::isNotBlank)
    }

    fun blocksUser(userId: String?): Boolean =
        userId
            ?.let(::normalizePirateUserId)
            ?.takeIf(String::isNotBlank)
            ?.let { it in userIds }
            ?: false

    fun blocksXmtpInbox(inboxId: String?): Boolean =
        inboxId
            ?.let(::normalizeXmtpInbox)
            ?.takeIf(String::isNotBlank)
            ?.let { it in xmtpInboxes }
            ?: false
}

@Serializable
internal data class UserBlockLedger(
    val accounts: Map<String, List<BlockedUser>> = emptyMap(),
) {
    fun entriesFor(viewerUserId: String): List<BlockedUser> =
        accounts[normalizePirateUserId(viewerUserId)].orEmpty()

    fun withBlocked(viewerUserId: String, blockedUser: BlockedUser): UserBlockLedger {
        val viewerKey = normalizePirateUserId(viewerUserId)
        val blockedKey = normalizePirateUserId(blockedUser.userId)
        require(viewerKey.isNotBlank()) { "Sign in before blocking a user." }
        require(blockedKey.isNotBlank()) { "This user cannot be blocked." }
        require(viewerKey != blockedKey) { "You cannot block yourself." }

        val normalizedRecord = blockedUser.copy(
            userId = blockedUser.userId.trim(),
            handleLabel = blockedUser.handleLabel?.trim()?.takeIf(String::isNotBlank),
            xmtpInbox = blockedUser.xmtpInbox?.trim()?.takeIf(String::isNotBlank),
        )
        val updated = (entriesFor(viewerKey).filterNot {
            normalizePirateUserId(it.userId) == blockedKey
        } + normalizedRecord).sortedByDescending(BlockedUser::blockedAtEpochMs)
        return copy(accounts = accounts + (viewerKey to updated))
    }

    fun withoutBlocked(viewerUserId: String, blockedUserId: String): UserBlockLedger {
        val viewerKey = normalizePirateUserId(viewerUserId)
        val blockedKey = normalizePirateUserId(blockedUserId)
        if (viewerKey.isBlank() || blockedKey.isBlank()) return this
        val updated = entriesFor(viewerKey).filterNot {
            normalizePirateUserId(it.userId) == blockedKey
        }
        return copy(accounts = if (updated.isEmpty()) accounts - viewerKey else accounts + (viewerKey to updated))
    }
}

class UserBlockStore internal constructor(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val cipher: SessionCipher,
    private val nowEpochMs: () -> Long,
) {
    constructor(context: Context, sessionStore: SessionStore) : this(
        context = context,
        sessionStore = sessionStore,
        cipher = AndroidKeystoreSessionCipher(keyAlias = "pirate_user_blocks_v1"),
        nowEpochMs = System::currentTimeMillis,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun observe(): Flow<UserBlockState> = combine(
        sessionStore.observe(),
        context.userBlockDataStore.data.map { preferences -> decode(preferences[KEY_ENCRYPTED_BLOCKS]) },
    ) { session, ledger ->
        val viewerUserId = session?.user?.userId?.trim()?.takeIf(String::isNotBlank)
        UserBlockState(
            viewerUserId = viewerUserId,
            users = viewerUserId?.let(ledger::entriesFor).orEmpty(),
        )
    }.distinctUntilChanged()

    suspend fun getState(): UserBlockState {
        val viewerUserId = sessionStore.get()?.user?.userId?.trim()?.takeIf(String::isNotBlank)
        val ledger = decode(context.userBlockDataStore.data.first()[KEY_ENCRYPTED_BLOCKS])
        return UserBlockState(
            viewerUserId = viewerUserId,
            users = viewerUserId?.let(ledger::entriesFor).orEmpty(),
        )
    }

    suspend fun block(userId: String, handleLabel: String? = null, xmtpInbox: String? = null) {
        val viewerUserId = sessionStore.get()?.user?.userId
            ?: throw IllegalStateException("Sign in before blocking a user.")
        update { ledger ->
            ledger.withBlocked(
                viewerUserId = viewerUserId,
                blockedUser = BlockedUser(
                    userId = userId,
                    handleLabel = handleLabel,
                    xmtpInbox = xmtpInbox,
                    blockedAtEpochMs = nowEpochMs(),
                ),
            )
        }
    }

    suspend fun unblock(userId: String) {
        val viewerUserId = sessionStore.get()?.user?.userId
            ?: throw IllegalStateException("Sign in before unblocking a user.")
        update { ledger -> ledger.withoutBlocked(viewerUserId, userId) }
    }

    private suspend fun update(transform: (UserBlockLedger) -> UserBlockLedger) {
        context.userBlockDataStore.edit { preferences ->
            val next = transform(decode(preferences[KEY_ENCRYPTED_BLOCKS]))
            preferences[KEY_ENCRYPTED_BLOCKS] = cipher.encrypt(json.encodeToString(UserBlockLedger.serializer(), next))
        }
    }

    private fun decode(encrypted: String?): UserBlockLedger {
        if (encrypted == null) return UserBlockLedger()
        return runCatching {
            json.decodeFromString(UserBlockLedger.serializer(), cipher.decrypt(encrypted))
        }.getOrDefault(UserBlockLedger())
    }
}

internal fun normalizePirateUserId(value: String): String = value.trim().lowercase()
internal fun normalizeXmtpInbox(value: String): String = value.trim().lowercase()
