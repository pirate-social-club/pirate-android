package sc.pirate.app.profile

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.PublicProfileResolution
import sc.pirate.app.safety.UserBlockState
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.FeedSkeletons

data class PublicProfileUiState(
    val loading: Boolean = true,
    val profile: PublicProfileResolution? = null,
    val error: String? = null,
    val viewerUserId: String? = null,
    val isBlocked: Boolean = false,
    val blockUpdating: Boolean = false,
    val blockMessage: String? = null,
)

class PublicProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository
    private var blockState = UserBlockState()

    private val _state = MutableStateFlow(PublicProfileUiState())
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.userBlockStore.observe().collect { nextBlockState ->
                blockState = nextBlockState
                _state.update { current ->
                    current.copy(
                        viewerUserId = nextBlockState.viewerUserId,
                        isBlocked = nextBlockState.blocksUser(current.profile?.profile?.userId),
                    )
                }
            }
        }
    }

    fun load(handleLabel: String) {
        if (handleLabel.isBlank()) {
            _state.value = PublicProfileUiState(
                loading = false,
                error = "Profile handle is unavailable.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = PublicProfileUiState(loading = true)
            try {
                _state.value = PublicProfileUiState(
                    loading = false,
                    profile = profileRepository.getPublicByHandle(handleLabel),
                    viewerUserId = blockState.viewerUserId,
                ).withBlockState(blockState)
            } catch (e: Exception) {
                _state.value = PublicProfileUiState(
                    loading = false,
                    error = e.message ?: "Could not load public profile",
                )
            }
        }
    }

    fun loadByWallet(walletAddress: String) {
        if (walletAddress.isBlank()) {
            _state.value = PublicProfileUiState(
                loading = false,
                error = "Profile wallet is unavailable.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = PublicProfileUiState(loading = true)
            try {
                _state.value = PublicProfileUiState(
                    loading = false,
                    profile = profileRepository.getPublicByWallet(walletAddress),
                    viewerUserId = blockState.viewerUserId,
                ).withBlockState(blockState)
            } catch (e: Exception) {
                _state.value = PublicProfileUiState(
                    loading = false,
                    error = e.message ?: "Could not load public profile",
                )
            }
        }
    }

    fun setBlocked(blocked: Boolean) {
        val profile = _state.value.profile?.profile ?: return
        if (_state.value.blockUpdating) return
        viewModelScope.launch {
            _state.update { it.copy(blockUpdating = true, blockMessage = null) }
            try {
                if (blocked) {
                    app.userBlockStore.block(
                        userId = profile.userId,
                        handleLabel = profile.displayHandle(),
                        xmtpInbox = profile.xmtpInbox,
                    )
                    app.chatService.blockPeer(profile.xmtpInbox)
                } else {
                    app.userBlockStore.unblock(profile.userId)
                    app.chatService.unblockPeer(profile.xmtpInbox)
                }
                _state.update {
                    it.copy(
                        isBlocked = blocked,
                        blockUpdating = false,
                        blockMessage = if (blocked) {
                            "User blocked. Their posts, comments, and direct messages are hidden."
                        } else {
                            "User unblocked."
                        },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        blockUpdating = false,
                        blockMessage = e.message ?: "Could not update this block.",
                    )
                }
            }
        }
    }

    fun clearBlockMessage() {
        _state.update { it.copy(blockMessage = null) }
    }
}

private fun PublicProfileUiState.withBlockState(blockState: UserBlockState): PublicProfileUiState =
    copy(isBlocked = blockState.blocksUser(profile?.profile?.userId))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    handleLabel: String,
    onNavigateToCommunity: (String) -> Unit,
    onBack: () -> Unit,
    onViewAvailability: (String) -> Unit,
    onMessage: ((String) -> Unit)? = null,
    walletAddress: String? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: PublicProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmBlockChange by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(handleLabel, walletAddress) {
        val wallet = walletAddress?.trim().orEmpty()
        if (wallet.isNotBlank()) viewModel.loadByWallet(wallet)
        else viewModel.load(handleLabel)
    }

    LaunchedEffect(state.blockMessage) {
        state.blockMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearBlockMessage()
        }
    }

    confirmBlockChange?.let { nextBlocked ->
        AlertDialog(
            onDismissRequest = { confirmBlockChange = null },
            title = { Text(if (nextBlocked) "Block this user?" else "Unblock this user?") },
            text = {
                Text(
                    if (nextBlocked) {
                        "Their posts and comments will be hidden, and existing XMTP direct messages will be denied on this device."
                    } else {
                        "Their content can appear again after feeds refresh, and direct messaging will be allowed."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBlockChange = null
                        viewModel.setBlocked(nextBlocked)
                    },
                ) { Text(if (nextBlocked) "Block" else "Unblock") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlockChange = null }) { Text("Cancel") }
            },
        )
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.profile?.resolvedHandleLabel ?: handleLabel,
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhosphorIcons.CaretLeft,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PirateTokens.colors.bgPage,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> {
                    FeedSkeletons(count = 2, modifier = Modifier.fillMaxSize())
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatusCard(
                            title = "Profile unavailable",
                            description = state.error.orEmpty(),
                            tone = StatusTone.Warning,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PirateButton(
                            text = "Retry",
                            onClick = { viewModel.load(handleLabel) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.profile != null -> {
                    val resolution = state.profile!!
                    val profile = resolution.profile
                    PirateProfilePage(
                        data = ProfilePageData(
                            profile = profile,
                            viewerContext = ViewerContext.Public,
                            stats = profile.followStats(),
                            walletAddress = profile.primaryWalletAddress,
                        ),
                        onMessage = onMessage,
                        onBook = { onViewAvailability(profile.userId) },
                        isBlocked = state.isBlocked,
                        blockUpdating = state.blockUpdating,
                        onToggleBlock = if (
                            state.viewerUserId != null &&
                            !state.viewerUserId.equals(profile.userId, ignoreCase = true)
                        ) {
                            { confirmBlockChange = !state.isBlocked }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun sc.pirate.app.api.model.Profile.followStats(): List<ProfileStat> =
    listOf(
        ProfileStat(label = "Followers", value = (followerCount ?: 0).toString()),
        ProfileStat(label = "Following", value = (followingCount ?: 0).toString()),
    )
