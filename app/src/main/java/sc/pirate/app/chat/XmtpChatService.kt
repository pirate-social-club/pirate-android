package sc.pirate.app.chat

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmtp.android.library.Client
import org.xmtp.android.library.ClientOptions
import org.xmtp.android.library.ConsentState
import org.xmtp.android.library.Conversation
import org.xmtp.android.library.XMTPEnvironment
import org.xmtp.android.library.libxmtp.GroupPermissionPreconfiguration

class XmtpChatService(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val peerResolver = XmtpPeerResolver(appContext)
    private var client: Client? = null
    private var connectedAddress: String? = null
    private var activeConversation: Conversation? = null
    private var chatVisible: Boolean = false

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _conversations = MutableStateFlow<List<ConversationItem>>(emptyList())
    val conversations: StateFlow<List<ConversationItem>> = _conversations

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _hideBottomNav = MutableStateFlow(false)
    val hideBottomNav: StateFlow<Boolean> = _hideBottomNav

    suspend fun connect(address: String) {
        val normalizedAddress = normalizeEthAddress(address)
        if (client != null && connectedAddress == normalizedAddress) return
        if (client != null && connectedAddress != normalizedAddress) disconnect()
        withContext(Dispatchers.IO) {
            val signer = getOrCreateLocalSigner(appContext, normalizedAddress)
            val options = ClientOptions(
                api = ClientOptions.Api(
                    env = xmtpEnvironment(),
                    isSecure = true,
                ),
                appContext = appContext,
                dbEncryptionKey = getOrCreateXmtpDbKey(appContext, signer.publicIdentity.identifier),
            )
            client = createXmtpClientWithDbRecovery(appContext, signer, options)
            connectedAddress = normalizedAddress
            _connected.value = true
            Log.i(TAG, "XMTP connected for $normalizedAddress")
            refreshConversations()
            startMessageStream()
        }
    }

    fun disconnect() {
        client = null
        connectedAddress = null
        activeConversation = null
        _connected.value = false
        _conversations.value = emptyList()
        _messages.value = emptyList()
        _activeConversationId.value = null
        _unreadCount.value = 0
        _hideBottomNav.value = false
        peerResolver.clearCaches()
    }

    fun currentInboxId(): String? = client?.inboxId

    fun setChatVisible(visible: Boolean) {
        chatVisible = visible
        if (visible) _unreadCount.value = 0
    }

    fun setBottomNavHidden(hidden: Boolean) {
        _hideBottomNav.value = hidden
    }

    suspend fun refreshConversations() {
        val safeClient = client ?: return
        runCatching {
            safeClient.conversations.syncAllConversations()
            val dms = safeClient.conversations.listDms()
            val groups = safeClient.conversations.listGroups()
            val dmItems = dms.mapNotNull { toDmConversationItem(safeClient, it, peerResolver, TAG) }
            val groupItems = groups.mapNotNull { toGroupConversationItem(it, TAG) }
            _conversations.value = (dmItems + groupItems).sortedByDescending { it.lastMessageTimestampMs }
        }.onFailure {
            Log.e(TAG, "refreshConversations failed", it)
        }
    }

    suspend fun openConversation(conversationId: String) {
        val safeClient = client ?: return
        _activeConversationId.value = conversationId
        runCatching {
            val conversation = safeClient.conversations.findConversation(conversationId) ?: return
            activeConversation = conversation
            conversation.sync()
            loadMessages(conversation)
        }.onFailure {
            Log.e(TAG, "openConversation failed", it)
        }
    }

    fun closeConversation() {
        activeConversation = null
        _activeConversationId.value = null
        _messages.value = emptyList()
    }

    suspend fun sendMessage(text: String) {
        val conversation = activeConversation ?: throw IllegalStateException("No active conversation")
        conversation.send(text)
        conversation.sync()
        loadMessages(conversation)
        refreshConversations()
    }

    suspend fun newDm(peerAddressOrInboxId: String): String {
        val safeClient = client ?: throw IllegalStateException("XMTP is not connected")
        return runCatching {
            createDmConversation(safeClient, peerAddressOrInboxId)
        }.getOrElse { firstError ->
            Log.w(TAG, "newDm failed on first attempt; retrying", firstError)
            delay(350)
            safeClient.conversations.syncAllConversations()
            createDmConversation(safeClient, peerAddressOrInboxId)
        }
    }

    private suspend fun createDmConversation(safeClient: Client, peerAddressOrInboxId: String): String {
        val inboxId = peerResolver.resolveInboxId(safeClient, peerAddressOrInboxId)
        Log.i(TAG, "Opening DM target=$peerAddressOrInboxId resolvedInboxId=$inboxId")
        val dm = safeClient.conversations.findOrCreateDm(inboxId)
        refreshConversations()
        return dm.id
    }

    suspend fun newGroup(
        memberTargets: List<String>,
        name: String?,
    ): String {
        val safeClient = client ?: throw IllegalStateException("XMTP is not connected")
        val memberInboxIds = memberTargets
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { peerResolver.resolveInboxId(safeClient, it) }
            .filter { it != safeClient.inboxId }
            .distinct()
        require(memberInboxIds.isNotEmpty()) { "Add at least one valid member" }

        val group = safeClient.conversations.newGroup(
            inboxIds = memberInboxIds,
            permissions = GroupPermissionPreconfiguration.ALL_MEMBERS,
            groupName = name?.trim().orEmpty(),
            groupImageUrlSquare = "",
            groupDescription = "",
        )
        refreshConversations()
        return group.id
    }

    private suspend fun loadMessages(conversation: Conversation) {
        val safeClient = client ?: return
        runCatching {
            val myInboxId = safeClient.inboxId
            _messages.value = conversation.messages(limit = 100).mapNotNull { message ->
                val text = sanitizeXmtpBody(message)
                if (text.isBlank()) return@mapNotNull null
                val senderInboxId = message.senderInboxId
                val isFromMe = senderInboxId == myInboxId
                ChatMessage(
                    id = message.id,
                    senderAddress = if (isFromMe) senderInboxId else peerResolver.resolvePeerAddress(safeClient, senderInboxId),
                    senderInboxId = senderInboxId,
                    text = text,
                    timestampMs = message.sentAtNs / 1_000_000,
                    isFromMe = isFromMe,
                )
            }.sortedBy { it.timestampMs }
        }.onFailure {
            Log.e(TAG, "loadMessages failed", it)
        }
    }

    private fun startMessageStream() {
        scope.launch {
            runCatching {
                client?.conversations
                    ?.streamAllMessages(consentStates = listOf(ConsentState.ALLOWED))
                    ?.collect { message ->
                        val safeClient = client
                        if (
                            safeClient != null &&
                            message.senderInboxId != safeClient.inboxId &&
                            !chatVisible
                        ) {
                            _unreadCount.value = (_unreadCount.value + 1).coerceAtMost(99)
                        }
                        refreshConversations()
                        activeConversation?.let { conversation ->
                            conversation.sync()
                            loadMessages(conversation)
                        }
                    }
            }.onFailure {
                Log.e(TAG, "Message stream failed", it)
            }
        }
    }

    private companion object {
        const val TAG = "XmtpChatService"

        fun xmtpEnvironment(): XMTPEnvironment =
            when (sc.pirate.app.BuildConfig.XMTP_ENVIRONMENT.trim().lowercase()) {
                "dev", "development" -> XMTPEnvironment.DEV
                else -> XMTPEnvironment.PRODUCTION
            }
    }
}
