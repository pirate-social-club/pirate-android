package sc.pirate.app.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.shortAddress
import java.text.DateFormat
import java.util.Date

private enum class ChatView {
    Conversations,
    Thread,
    NewDm,
    NewGroup,
}

@Composable
fun ChatScreen(
    chatService: XmtpChatService,
    isAuthenticated: Boolean,
    userAddress: String?,
    onShowMessage: (String) -> Unit,
    onConnected: suspend (String) -> Unit = {},
    onOpenWallet: () -> Unit = {},
    onOpenPeerProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val connected by chatService.connected.collectAsState()
    val conversations by chatService.conversations.collectAsState()
    val messages by chatService.messages.collectAsState()
    val activeConversationId by chatService.activeConversationId.collectAsState()
    val activeConversation = remember(activeConversationId, conversations) {
        conversations.firstOrNull { it.id == activeConversationId }
    }

    var view by rememberSaveable { mutableStateOf(ChatView.Conversations) }
    var connecting by remember { mutableStateOf(false) }
    var openingConversation by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isAuthenticated, userAddress, connected) {
        if (!isAuthenticated || userAddress.isNullOrBlank() || connected || connecting) return@LaunchedEffect
        connecting = true
        error = null
        runCatching {
            chatService.connect(userAddress)
            chatService.currentInboxId()?.let { onConnected(it) }
        }
            .onFailure {
                error = it.message ?: "Could not connect to XMTP"
                onShowMessage(error.orEmpty())
            }
        connecting = false
    }

    LaunchedEffect(activeConversationId) {
        view = if (activeConversationId == null) ChatView.Conversations else ChatView.Thread
    }

    DisposableEffect(Unit) {
        chatService.setChatVisible(true)
        onDispose {
            scope.cancel()
            chatService.setChatVisible(false)
            chatService.setBottomNavHidden(false)
            chatService.closeConversation()
        }
    }

    LaunchedEffect(view) {
        chatService.setBottomNavHidden(view != ChatView.Conversations)
    }

    when (view) {
        ChatView.Conversations -> ConversationListScreen(
            conversations = conversations,
            isAuthenticated = isAuthenticated,
            connecting = connecting,
            error = error,
            onNewDm = { view = ChatView.NewDm },
            onNewGroup = { view = ChatView.NewGroup },
            onOpenConversation = { conversationId ->
                scope.launch { chatService.openConversation(conversationId) }
            },
            modifier = modifier,
        )
        ChatView.Thread -> {
            if (activeConversation == null) {
                ConversationListScreen(
                    conversations = conversations,
                    isAuthenticated = isAuthenticated,
                    connecting = connecting,
                    error = error,
                    onNewDm = { view = ChatView.NewDm },
                    onNewGroup = { view = ChatView.NewGroup },
                    onOpenConversation = { conversationId -> scope.launch { chatService.openConversation(conversationId) } },
                    modifier = modifier,
                )
            } else {
                MessageThreadScreen(
                    conversation = activeConversation,
                    messages = messages,
                    onBack = {
                        chatService.closeConversation()
                        view = ChatView.Conversations
                    },
                    onOpenProfile = activeConversation.peerAddress
                        ?.takeIf { activeConversation.type == ConversationType.DM && looksLikeEthereumAddress(it) }
                        ?.let { address -> { onOpenPeerProfile(address) } },
                    onSend = { text ->
                        scope.launch {
                            runCatching { chatService.sendMessage(text) }
                                .onFailure {
                                    if (!it.isUiCancellation()) {
                                        onShowMessage("Send failed: ${it.message ?: "unknown error"}")
                                    }
                                }
                        }
                    },
                    modifier = modifier,
                )
            }
        }
        ChatView.NewDm -> NewDmScreen(
            canSendMessages = !userAddress.isNullOrBlank(),
            connecting = connecting,
            connected = connected,
            opening = openingConversation,
            onBack = { view = ChatView.Conversations },
            onOpenWallet = onOpenWallet,
            onCreate = { target ->
                scope.launch {
                    runCatching {
                        openingConversation = true
                        if (!connected) {
                            val address = userAddress ?: throw IllegalStateException("Missing wallet address")
                            connecting = true
                            chatService.connect(address)
                            chatService.currentInboxId()?.let { onConnected(it) }
                            connecting = false
                        }
                        val dmId = chatService.newDm(target)
                        chatService.openConversation(dmId)
                    }.onFailure {
                        connecting = false
                        if (!it.isUiCancellation()) {
                            onShowMessage("New DM failed: ${it.message ?: "unknown error"}")
                        }
                    }.also {
                        openingConversation = false
                    }
                }
            },
            modifier = modifier,
        )
        ChatView.NewGroup -> NewGroupScreen(
            canSendMessages = !userAddress.isNullOrBlank(),
            connecting = connecting,
            connected = connected,
            opening = openingConversation,
            onBack = { view = ChatView.Conversations },
            onOpenWallet = onOpenWallet,
            onCreate = { name, members ->
                scope.launch {
                    runCatching {
                        openingConversation = true
                        if (!connected) {
                            val address = userAddress ?: throw IllegalStateException("Missing wallet address")
                            connecting = true
                            chatService.connect(address)
                            chatService.currentInboxId()?.let { onConnected(it) }
                            connecting = false
                        }
                        val groupId = chatService.newGroup(memberTargets = members, name = name)
                        chatService.openConversation(groupId)
                    }.onFailure {
                        connecting = false
                        if (!it.isUiCancellation()) {
                            onShowMessage("Create group failed: ${it.message ?: "unknown error"}")
                        }
                    }.also {
                        openingConversation = false
                    }
                }
            },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListScreen(
    conversations: List<ConversationItem>,
    isAuthenticated: Boolean,
    connecting: Boolean,
    error: String?,
    onNewDm: () -> Unit,
    onNewGroup: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat", color = PirateTokens.colors.textPrimary) },
                actions = {
                    IconButton(onClick = onNewDm, enabled = isAuthenticated && !connecting) {
                        Icon(PhosphorIcons.Plus, contentDescription = "New message", tint = PirateTokens.colors.textPrimary)
                    }
                    IconButton(onClick = onNewGroup, enabled = isAuthenticated && !connecting) {
                        Icon(PhosphorIcons.Users, contentDescription = "New group", tint = PirateTokens.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = PirateTokens.colors.bgPage,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!isAuthenticated) {
                item { EmptyChatState("Sign in to message other users.") }
            } else if (connecting) {
                item { ConnectingState() }
            } else if (error != null) {
                item { EmptyChatState(error) }
            } else if (conversations.isEmpty()) {
                item { EmptyChatState("No conversations yet.") }
            } else {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation = conversation, onClick = { onOpenConversation(conversation.id) })
                    HorizontalDivider(color = PirateTokens.colors.borderSoft)
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityCircle(label = conversation.displayName, avatarUri = conversation.avatarUri)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = displayConversationName(conversation),
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = conversation.lastMessage.ifBlank { conversation.subtitle.orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageThreadScreen(
    conversation: ConversationItem,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onOpenProfile: (() -> Unit)?,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val nearBottom = lastVisible == null || lastVisible >= messages.lastIndex - 1
        if (nearBottom) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    ThreadHeaderTitle(
                        title = displayConversationName(conversation),
                        avatarUri = conversation.avatarUri,
                        onClick = onOpenProfile,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PhosphorIcons.CaretLeft, contentDescription = "Back", tint = PirateTokens.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = PirateTokens.colors.bgPage,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        showSenderLabel = conversation.type == ConversationType.GROUP,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(PirateTokens.radius.full),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isNotBlank()) {
                            input = ""
                            onSend(text)
                        }
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Icon(PhosphorIcons.PaperPlaneRight, contentDescription = "Send", tint = PirateTokens.colors.accentBrand)
                }
            }
        }
    }
}

@Composable
private fun ThreadHeaderTitle(
    title: String,
    avatarUri: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IdentityCircle(label = title, avatarUri = avatarUri, size = 28.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = PirateTokens.colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showSenderLabel: Boolean,
) {
    val alignment = if (message.isFromMe) Alignment.End else Alignment.Start
    val bgColor = if (message.isFromMe) PirateTokens.colors.accentBrand else PirateTokens.colors.bgElevated
    val textColor = if (message.isFromMe) PirateTokens.colors.textOnAccent else PirateTokens.colors.textPrimary
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (showSenderLabel && !message.isFromMe) {
            Text(
                text = displaySenderLabel(message.senderAddress),
                color = PirateTokens.colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(start = 4.dp, bottom = 2.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(PirateTokens.radius.lg),
            color = bgColor,
            border = if (message.isFromMe) null else BorderStroke(1.dp, PirateTokens.colors.borderSoft),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.text, color = textColor, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.timestampMs)),
                    color = textColor.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewDmScreen(
    canSendMessages: Boolean,
    connecting: Boolean,
    connected: Boolean,
    opening: Boolean,
    onBack: () -> Unit,
    onOpenWallet: () -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var target by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Message", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PhosphorIcons.CaretLeft, contentDescription = "Back", tint = PirateTokens.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = PirateTokens.colors.bgPage,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!canSendMessages) {
                WalletRequiredMessage(onOpenWallet = onOpenWallet)
            } else {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Handle or wallet address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.weight(1f))
                PirateButton(
                    text = when {
                        opening -> "Opening..."
                        connecting && !connected -> "Connecting..."
                        else -> "Message"
                    },
                    onClick = { onCreate(target.trim()) },
                    enabled = target.isNotBlank() && !connecting && !opening,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewGroupScreen(
    canSendMessages: Boolean,
    connecting: Boolean,
    connected: Boolean,
    opening: Boolean,
    onBack: () -> Unit,
    onOpenWallet: () -> Unit,
    onCreate: (String, List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var groupName by rememberSaveable { mutableStateOf("") }
    var memberInput by rememberSaveable { mutableStateOf("") }
    var members by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun addMember() {
        val normalized = memberInput.trim()
        if (normalized.isBlank()) return
        members = (members + normalized).distinct()
        memberInput = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Group", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PhosphorIcons.CaretLeft, contentDescription = "Back", tint = PirateTokens.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        containerColor = PirateTokens.colors.bgPage,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!canSendMessages) {
                WalletRequiredMessage(onOpenWallet = onOpenWallet)
            } else {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = memberInput,
                        onValueChange = { memberInput = it },
                        label = { Text("Handle or wallet address") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PirateButton(
                        text = "Add",
                        onClick = ::addMember,
                        enabled = memberInput.isNotBlank(),
                        variant = ButtonVariant.Outline,
                    )
                }
                if (members.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { member ->
                            Surface(
                                color = PirateTokens.colors.bgElevated,
                                border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
                                shape = RoundedCornerShape(PirateTokens.radius.md),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = member,
                                        color = PirateTokens.colors.textPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { members = members.filterNot { it == member } }) {
                                        Text("Remove", color = PirateTokens.colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                PirateButton(
                    text = when {
                        opening -> "Opening..."
                        connecting && !connected -> "Connecting..."
                        else -> "Create group"
                    },
                    onClick = { onCreate(groupName.trim(), members) },
                    enabled = members.isNotEmpty() && !connecting && !opening,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WalletRequiredMessage(onOpenWallet: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Link a wallet to send messages",
            color = PirateTokens.colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Encrypted messages need a wallet-backed XMTP identity.",
            color = PirateTokens.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        PirateButton(
            text = "Open wallet",
            onClick = onOpenWallet,
            leadingIcon = PhosphorIcons.Wallet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyChatState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = PirateTokens.colors.textSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ConnectingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), color = PirateTokens.colors.accentBrand)
        Text("Connecting to XMTP...", color = PirateTokens.colors.textSecondary)
    }
}

@Composable
private fun IdentityCircle(
    label: String,
    avatarUri: String?,
    size: Dp = 42.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(Modifier)
            .padding(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = PirateTokens.colors.bgElevated,
            border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = PirateTokens.colors.textSecondary,
                    style = if (size < 36.dp) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
                )
                if (!avatarUri.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun displayConversationName(conversation: ConversationItem): String {
    val raw = conversation.displayName.ifBlank { conversation.peerAddress ?: conversation.id }
    return if (looksLikeEthereumAddress(raw)) shortAddress(raw) else raw
}

private fun displaySenderLabel(senderAddress: String): String =
    if (looksLikeEthereumAddress(senderAddress)) shortAddress(senderAddress)
    else senderAddress.takeIf { it.length <= 18 } ?: "${senderAddress.take(10)}...${senderAddress.takeLast(6)}"

private fun Throwable.isUiCancellation(): Boolean =
    this is CancellationException ||
        message?.contains("coroutine scope left the composition", ignoreCase = true) == true
