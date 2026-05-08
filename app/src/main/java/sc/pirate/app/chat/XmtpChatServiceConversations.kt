package sc.pirate.app.chat

import android.util.Log
import org.xmtp.android.library.Client
import org.xmtp.android.library.Dm
import org.xmtp.android.library.Group

internal suspend fun toDmConversationItem(
    client: Client,
    dm: Dm,
    peerResolver: XmtpPeerResolver,
    tag: String,
): ConversationItem? {
    return try {
        val last = dm.lastMessage()
        val peerInboxId = dm.peerInboxId
        if (!looksLikeXmtpInboxId(peerInboxId)) {
            Log.w(tag, "Ignoring malformed DM peer inbox id=$peerInboxId")
            return null
        }
        val peerAddress = peerResolver.resolvePeerAddress(client, peerInboxId)
        val peerIdentity = peerResolver.resolvePeerIdentity(peerAddress)
        ConversationItem(
            id = dm.id,
            type = ConversationType.DM,
            displayName = peerIdentity.displayName.ifBlank { peerAddress },
            avatarUri = peerIdentity.avatarUri,
            lastMessage = last?.let { sanitizeXmtpBody(it) } ?: "",
            lastMessageTimestampMs = last?.sentAtNs?.div(1_000_000) ?: 0L,
            subtitle = peerInboxId,
            peerAddress = peerAddress,
            peerInboxId = peerInboxId,
        )
    } catch (error: Exception) {
        Log.w(tag, "Failed to read DM conversation", error)
        null
    }
}

internal suspend fun toGroupConversationItem(group: Group, tag: String): ConversationItem? {
    return try {
        val last = group.lastMessage()
        ConversationItem(
            id = group.id,
            type = ConversationType.GROUP,
            displayName = group.name().trim().ifBlank { "Untitled group" },
            lastMessage = last?.let { sanitizeXmtpBody(it) } ?: "",
            lastMessageTimestampMs = last?.sentAtNs?.div(1_000_000) ?: 0L,
            subtitle = group.description().trim().ifBlank { "Group chat" },
        )
    } catch (error: Exception) {
        Log.w(tag, "Failed to read group conversation", error)
        null
    }
}
