package sc.pirate.app.chat

import android.content.Context
import android.util.Log
import org.xmtp.android.library.Client
import org.xmtp.android.library.libxmtp.IdentityKind
import org.xmtp.android.library.libxmtp.PublicIdentity
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.Profile
import sc.pirate.app.shared.buildDefaultUserAvatarSrc
import sc.pirate.app.shared.resolvePublicMediaSrc

private const val TAG = "XmtpPeerResolver"

internal data class ResolvedPeerIdentity(
    val displayName: String,
    val avatarUri: String?,
)

internal class XmtpPeerResolver(context: Context) {
    private val app = context.applicationContext as PirateApp
    private val prefs = context.getSharedPreferences("xmtp_identity_resolver", Context.MODE_PRIVATE)
    private val peerAddressByInboxId = mutableMapOf<String, String>()
    private val peerIdentityByAddress = mutableMapOf<String, ResolvedPeerIdentity>()

    suspend fun resolveInboxId(client: Client, rawAddressOrInboxId: String): String {
        val trimmed = rawAddressOrInboxId.trim()
        require(trimmed.isNotBlank()) { "Missing address or inbox ID" }
        val handleTarget = normalizeHandleTarget(trimmed)

        val normalizedAddress =
            when {
                trimmed.startsWith("0x", ignoreCase = true) -> normalizeEthAddress(trimmed)
                trimmed.length == 40 && trimmed.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } ->
                    normalizeEthAddress("0x$trimmed")
                else -> null
            }

        if (normalizedAddress != null) {
            val byIdentity = client.inboxIdFromIdentity(PublicIdentity(IdentityKind.ETHEREUM, normalizedAddress))
            if (!byIdentity.isNullOrBlank()) return byIdentity
            throw IllegalStateException("No XMTP inboxId for address=$normalizedAddress")
        }

        if (looksLikeXmtpInboxId(trimmed)) return trimmed

        val resolvedInboxId = handleTarget?.let { resolveHandleLikeTarget(client, it) }
        if (!resolvedInboxId.isNullOrBlank()) return resolvedInboxId

        throw IllegalStateException("No XMTP inbox for ${handleTarget ?: trimmed}")
    }

    private suspend fun resolveHandleLikeTarget(client: Client, rawTarget: String): String? {
        val handle = rawTarget.trim().takeIf {
            it.isNotBlank() && it.length <= 120 && it.none(Char::isWhitespace)
        } ?: return null

        return runCatching {
            val profile = app.apiClient.profiles.getPublicByHandle(handle).profile
            profile.xmtpInbox?.trim()?.takeIf { it.isNotBlank() }?.let { return@runCatching it }
            val address = profile.primaryWalletAddress?.trim()?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val normalizedAddress = normalizeEthAddress(address)
            client.inboxIdFromIdentity(PublicIdentity(IdentityKind.ETHEREUM, normalizedAddress))
        }.onFailure {
            Log.d(TAG, "Handle XMTP lookup failed target=$rawTarget", it)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun normalizeHandleTarget(rawTarget: String): String? {
        var value = rawTarget.trim()
            .removePrefix("@")
            .substringBefore("?")
            .substringBefore("#")
            .trim('/')

        value = value
            .removePrefix("https://")
            .removePrefix("http://")
            .trim('/')

        if (value.startsWith("pirate.sc/", ignoreCase = true)) {
            value = value.substringAfter('/').trim('/')
        }
        if (value.startsWith("u/", ignoreCase = true)) {
            value = value.drop(2).trim('/')
        }

        return value.removePrefix("@").takeIf { it.isNotBlank() }
    }

    suspend fun resolvePeerAddress(client: Client, peerInboxId: String): String {
        peerAddressByInboxId[peerInboxId]?.let { return it }
        loadPersistedAddressForInbox(peerInboxId)?.let {
            peerAddressByInboxId[peerInboxId] = it
            return it
        }

        return runCatching {
            val localState = client.inboxStatesForInboxIds(false, listOf(peerInboxId)).firstOrNull()
            val state = localState ?: client.inboxStatesForInboxIds(true, listOf(peerInboxId)).firstOrNull()
            val identity = state
                ?.identities
                ?.firstOrNull { it.kind == IdentityKind.ETHEREUM }
                ?.identifier
            val normalized = identity?.let(::normalizeEthAddressOrNull)
            if (normalized != null) {
                peerAddressByInboxId[peerInboxId] = normalized
                persistInboxAddress(peerInboxId, normalized)
                normalized
            } else {
                peerInboxId
            }
        }.getOrElse {
            Log.w(TAG, "resolvePeerAddress failed inbox=$peerInboxId", it)
            peerInboxId
        }
    }

    suspend fun resolvePeerIdentity(addressOrInboxId: String): ResolvedPeerIdentity {
        val normalizedAddress = normalizeEthAddressOrNull(addressOrInboxId)
            ?: return ResolvedPeerIdentity(addressOrInboxId, null)
        peerIdentityByAddress[normalizedAddress]?.let { return it }

        return runCatching {
            val resolution = app.apiClient.profiles.getPublicByWallet(normalizedAddress)
            val profile = resolution.profile
            val identity = ResolvedPeerIdentity(
                displayName = profile.chatDisplayName(resolution.resolvedHandleLabel),
                avatarUri = profile.chatAvatarUri(resolution.resolvedHandleLabel),
            )
            peerIdentityByAddress[normalizedAddress] = identity
            identity
        }.getOrElse {
            Log.d(TAG, "Profile lookup failed address=$normalizedAddress", it)
            ResolvedPeerIdentity(normalizedAddress, null)
        }
    }

    fun clearCaches() {
        peerAddressByInboxId.clear()
        peerIdentityByAddress.clear()
    }

    private fun loadPersistedAddressForInbox(inboxId: String): String? =
        prefs.getString("inbox_address:${inboxId.lowercase()}", null)?.trim()?.takeIf { it.isNotBlank() }

    private fun persistInboxAddress(inboxId: String, address: String) {
        prefs.edit().putString("inbox_address:${inboxId.lowercase()}", address.lowercase()).apply()
    }

    private fun Profile.chatDisplayName(resolvedHandleLabel: String): String =
        displayName?.trim()?.takeIf { it.isNotBlank() }
            ?: resolvedHandleLabel.trim().takeIf { it.isNotBlank() }
            ?: primaryWalletAddress?.let(::normalizeEthAddressOrNull)
            ?: userId.takeIf { it.isNotBlank() }
            ?: "Pirate"

    private fun Profile.chatAvatarUri(resolvedHandleLabel: String): String? {
        val seed = userId
            .takeIf { it.isNotBlank() }
            ?: primaryWalletAddress?.trim()
            ?: resolvedHandleLabel.trim()
        return resolvePublicMediaSrc(avatarRef)
            ?: buildDefaultUserAvatarSrc(seed)
                .takeIf { it.isNotBlank() }
    }
}
