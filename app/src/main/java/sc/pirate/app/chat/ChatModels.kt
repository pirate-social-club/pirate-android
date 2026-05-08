package sc.pirate.app.chat

enum class ConversationType {
    DM,
    GROUP,
}

data class ConversationItem(
    val id: String,
    val type: ConversationType,
    val displayName: String,
    val avatarUri: String? = null,
    val lastMessage: String,
    val lastMessageTimestampMs: Long,
    val subtitle: String? = null,
    val peerAddress: String? = null,
    val peerInboxId: String? = null,
)

data class ChatMessage(
    val id: String,
    val senderAddress: String,
    val senderInboxId: String,
    val text: String,
    val timestampMs: Long,
    val isFromMe: Boolean,
)
