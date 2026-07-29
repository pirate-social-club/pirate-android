package sc.pirate.app.settings

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ProfileUpdateInput
import sc.pirate.app.api.model.Profile
import sc.pirate.app.profile.displayHandle
import sc.pirate.app.legal.CURRENT_TERMS_VERSION
import sc.pirate.app.legal.TermsAcceptance
import sc.pirate.app.safety.BlockedUser
import sc.pirate.app.shared.buildDefaultProfileCoverSrc
import sc.pirate.app.shared.buildDefaultUserAvatarSrc
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.theme.AppearanceMode
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

private const val DISPLAY_NAME_MAX = 50
private const val BIO_MAX = 300

data class SettingsUiState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val displayName: String = "",
    val bio: String = "",
    val handleLabel: String = "",
    val preferredLocale: String = "",
    val pendingAvatarUri: Uri? = null,
    val pendingAvatarLabel: String? = null,
    val pendingCoverUri: Uri? = null,
    val pendingCoverLabel: String? = null,
    val avatarRemoved: Boolean = false,
    val coverRemoved: Boolean = false,
    val savingProfile: Boolean = false,
    val savingPreferences: Boolean = false,
    val renamingHandle: Boolean = false,
    val handleExpanded: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val displayNameError: String? = null,
    val blockedUsers: List<BlockedUser> = emptyList(),
    val unblockingUserIds: Set<String> = emptySet(),
    val termsAcceptance: TermsAcceptance? = null,
    val appearanceMode: AppearanceMode = AppearanceMode.System,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository
    private val contentResolver get() = getApplication<Application>().contentResolver
    private var observedBlockedUsers: List<BlockedUser> = emptyList()
    private var observedAppearanceMode: AppearanceMode = AppearanceMode.System

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            app.userBlockStore.observe().collect { blockState ->
                observedBlockedUsers = blockState.users
                _state.value = _state.value.copy(blockedUsers = blockState.users)
            }
        }
        viewModelScope.launch {
            app.appearanceStore.observe().collect { mode ->
                observedAppearanceMode = mode
                _state.value = _state.value.copy(appearanceMode = mode)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, message = null)
            try {
                val profile = profileRepository.getMe()
                val termsAcceptance = app.termsAcceptanceStore.currentAcceptance()
                _state.value = SettingsUiState(
                    loading = false,
                    profile = profile,
                    displayName = profile.displayName.orEmpty(),
                    bio = profile.bio.orEmpty(),
                    blockedUsers = observedBlockedUsers,
                    handleLabel = profile.globalHandle?.label.orEmpty(),
                    preferredLocale = profile.preferredLocale.orEmpty(),
                    termsAcceptance = termsAcceptance,
                    appearanceMode = observedAppearanceMode,
                )
            } catch (e: Exception) {
                _state.value = SettingsUiState(
                    loading = false,
                    error = e.message ?: "Could not load settings",
                )
            }
        }
    }

    fun unblockUser(blockedUser: BlockedUser) {
        if (blockedUser.userId in _state.value.unblockingUserIds) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                unblockingUserIds = _state.value.unblockingUserIds + blockedUser.userId,
                error = null,
                message = null,
            )
            try {
                app.userBlockStore.unblock(blockedUser.userId)
                app.chatService.unblockPeer(blockedUser.xmtpInbox)
                _state.value = _state.value.copy(
                    unblockingUserIds = _state.value.unblockingUserIds - blockedUser.userId,
                    message = "User unblocked.",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    unblockingUserIds = _state.value.unblockingUserIds - blockedUser.userId,
                    error = e.message ?: "Could not unblock this user.",
                )
            }
        }
    }

    fun updateDisplayName(value: String) {
        _state.value = _state.value.copy(
            displayName = value.take(DISPLAY_NAME_MAX),
            displayNameError = null,
            error = null,
            message = null,
        )
    }

    fun updateBio(value: String) {
        _state.value = _state.value.copy(bio = value.take(BIO_MAX), error = null, message = null)
    }

    fun updateHandle(value: String) {
        _state.value = _state.value.copy(handleLabel = value, error = null, message = null)
    }

    fun setHandleExpanded(expanded: Boolean) {
        _state.value = _state.value.copy(handleExpanded = expanded, error = null, message = null)
    }

    fun updatePreferredLocale(value: String) {
        _state.value = _state.value.copy(preferredLocale = value, error = null, message = null)
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        if (mode == _state.value.appearanceMode) return
        _state.value = _state.value.copy(appearanceMode = mode, error = null, message = null)
        viewModelScope.launch {
            try {
                app.appearanceStore.set(mode)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    appearanceMode = observedAppearanceMode,
                    error = e.message ?: "Could not save appearance preference.",
                )
            }
        }
    }

    fun selectAvatar(uri: Uri?) {
        _state.value = _state.value.copy(
            pendingAvatarUri = uri,
            pendingAvatarLabel = uri?.displayName(),
            avatarRemoved = false,
            error = null,
            message = null,
        )
    }

    fun selectCover(uri: Uri?) {
        _state.value = _state.value.copy(
            pendingCoverUri = uri,
            pendingCoverLabel = uri?.displayName(),
            coverRemoved = false,
            error = null,
            message = null,
        )
    }

    fun removeAvatar() {
        _state.value = _state.value.copy(
            pendingAvatarUri = null,
            pendingAvatarLabel = null,
            avatarRemoved = true,
            error = null,
            message = null,
        )
    }

    fun removeCover() {
        _state.value = _state.value.copy(
            pendingCoverUri = null,
            pendingCoverLabel = null,
            coverRemoved = true,
            error = null,
            message = null,
        )
    }

    fun saveProfile() {
        viewModelScope.launch {
            val current = _state.value
            val trimmedName = current.displayName.trim()
            if (trimmedName.isBlank()) {
                _state.value = current.copy(displayNameError = "Display name is required.", error = null, message = null)
                return@launch
            }
            if (!current.profileHasChanges()) {
                return@launch
            }

            _state.value = current.copy(savingProfile = true, error = null, message = null)
            if (!app.termsAcceptanceManager.requireForUgc()) {
                _state.value = _state.value.copy(savingProfile = false)
                return@launch
            }
            try {
                val avatarRef = current.pendingAvatarUri?.uploadProfileMedia("avatar")
                val coverRef = current.pendingCoverUri?.uploadProfileMedia("cover")
                val profile = profileRepository.updateMe(
                    ProfileUpdateInput(
                        displayName = trimmedName,
                        bio = current.bio.trim().ifBlank { null },
                        bioSource = if (current.bio != current.profile?.bio.orEmpty()) "manual" else null,
                        avatarRef = avatarRef,
                        avatarSource = if (current.avatarRemoved) "none" else null,
                        coverRef = coverRef,
                        coverSource = if (current.coverRemoved) "none" else null,
                    ),
                )
                _state.value = _state.value.copy(
                    profile = profile,
                    displayName = profile.displayName.orEmpty(),
                    bio = profile.bio.orEmpty(),
                    preferredLocale = profile.preferredLocale.orEmpty(),
                    pendingAvatarUri = null,
                    pendingAvatarLabel = null,
                    pendingCoverUri = null,
                    pendingCoverLabel = null,
                    avatarRemoved = false,
                    coverRemoved = false,
                    savingProfile = false,
                    message = "Profile updated",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    savingProfile = false,
                    error = e.message ?: "Failed to save profile.",
                )
            }
        }
    }

    fun savePreferences() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(savingPreferences = true, error = null, message = null)
            try {
                val profile = profileRepository.updateMe(
                    ProfileUpdateInput(
                        preferredLocale = current.preferredLocale.trim().ifBlank { null },
                    ),
                )
                _state.value = _state.value.copy(
                    profile = profile,
                    preferredLocale = profile.preferredLocale.orEmpty(),
                    savingPreferences = false,
                    message = "Preferences updated",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    savingPreferences = false,
                    error = e.message ?: "Failed to save preferences.",
                )
            }
        }
    }

    fun renameHandle() {
        val desiredLabel = _state.value.handleLabel
            .trim()
            .removePrefix("@")
            .removeSuffix(".pirate")
            .trim()
        if (desiredLabel.isBlank()) {
            _state.value = _state.value.copy(error = "Handle is required.", message = null)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(renamingHandle = true, error = null, message = null)
            if (!app.termsAcceptanceManager.requireForUgc()) {
                _state.value = _state.value.copy(renamingHandle = false)
                return@launch
            }
            try {
                val result = profileRepository.renameHandle(desiredLabel)
                val profile = profileRepository.getMe()
                _state.value = _state.value.copy(
                    profile = profile,
                    displayName = profile.displayName.orEmpty(),
                    bio = profile.bio.orEmpty(),
                    handleLabel = result.label,
                    preferredLocale = profile.preferredLocale.orEmpty(),
                    renamingHandle = false,
                    handleExpanded = false,
                    message = "Handle updated to ${result.label}.",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    renamingHandle = false,
                    error = e.message ?: "Rename failed.",
                )
            }
        }
    }

    private fun Uri.displayName(): String {
        contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return lastPathSegment ?: "Selected image"
    }

    private suspend fun Uri.uploadProfileMedia(kind: String): String {
        val mimeType = contentResolver.getType(this) ?: "image/jpeg"
        val name = displayName()
        val bytes = contentResolver.openInputStream(this)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read selected image.")
        return profileRepository.uploadMedia(kind, bytes, name, mimeType)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    section: String?,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onNavigateToSection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val openAccountDeletion = {
        uriHandler.openUri("${sc.pirate.app.BuildConfig.WEB_BASE_URL.trimEnd('/')}/delete-account")
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val title = when (section) {
        "profile" -> "Profile"
        "preferences" -> "Preferences"
        "domains" -> "Domains"
        "agents" -> "Agents"
        "blocked" -> "Blocked users"
        "legal" -> "Terms & privacy"
        else -> "Settings"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (section == null) onClose else onBack) {
                        Icon(
                            imageVector = if (section == null) PhosphorIcons.X else PhosphorIcons.CaretLeft,
                            contentDescription = if (section == null) "Close" else "Back",
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
        when {
            state.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                }
            }

            state.profile == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusCard(
                        title = "Settings unavailable",
                        description = state.error ?: "Could not load profile settings.",
                        tone = StatusTone.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PirateButton(
                        text = "Retry",
                        onClick = viewModel::load,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PirateButton(
                        text = "Delete account",
                        onClick = openAccountDeletion,
                        variant = ButtonVariant.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            section == null -> {
                SettingsIndex(
                    onNavigateToSection = onNavigateToSection,
                    onOpenAccountDeletion = openAccountDeletion,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    item { Spacer(modifier = Modifier.height(2.dp)) }
                    if (state.message != null) {
                        item {
                            FormNote(
                                message = state.message.orEmpty(),
                                tone = FormTone.Warning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (state.error != null) {
                        item {
                            FormNote(
                                message = state.error.orEmpty(),
                                tone = FormTone.Error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    when (section) {
                        "preferences" -> item { PreferencesSettings(state, viewModel) }
                        "domains" -> item { DomainsSettings() }
                        "agents" -> item { AgentsSettings() }
                        "blocked" -> item { BlockedUsersSettings(state, viewModel) }
                        "legal" -> item { LegalSettings(state.termsAcceptance) }
                        else -> item { ProfileSettings(state, viewModel) }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SettingsIndex(
    onNavigateToSection: (String) -> Unit,
    onOpenAccountDeletion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { SettingsIndexRow("Profile", onClick = { onNavigateToSection("profile") }) }
        item { SettingsIndexRow("Domains", onClick = { onNavigateToSection("domains") }) }
        item { SettingsIndexRow("Preferences", onClick = { onNavigateToSection("preferences") }) }
        item { SettingsIndexRow("Agents", onClick = { onNavigateToSection("agents") }) }
        item { SettingsIndexRow("Blocked users", onClick = { onNavigateToSection("blocked") }) }
        item { SettingsIndexRow("Terms & privacy", onClick = { onNavigateToSection("legal") }) }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item { SettingsIndexRow("Delete account", onClick = onOpenAccountDeletion) }
    }
}

@Composable
private fun BlockedUsersSettings(state: SettingsUiState, viewModel: SettingsViewModel) {
    SettingsSection(title = "Blocked users") {
        if (state.blockedUsers.isEmpty()) {
            StatusCard(
                title = "No blocked users",
                description = "People you block will appear here so you can unblock them later.",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.blockedUsers.forEach { blockedUser ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PirateTokens.colors.bgElevated,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = blockedUser.handleLabel ?: blockedUser.userId,
                                style = MaterialTheme.typography.titleMedium,
                                color = PirateTokens.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            PirateButton(
                                text = "Unblock",
                                onClick = { viewModel.unblockUser(blockedUser) },
                                loading = blockedUser.userId in state.unblockingUserIds,
                                variant = ButtonVariant.Outline,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalSettings(acceptance: TermsAcceptance?) {
    val uriHandler = LocalUriHandler.current
    val webBaseUrl = sc.pirate.app.BuildConfig.WEB_BASE_URL.trimEnd('/')
    SettingsSection(title = "Terms & privacy") {
        StatusCard(
            title = if (acceptance?.version == CURRENT_TERMS_VERSION) "Terms accepted" else "Acceptance required before posting",
            description = if (acceptance?.version == CURRENT_TERMS_VERSION) {
                "Accepted version ${acceptance.version}. You will be asked again when a new version requires consent."
            } else {
                "Pirate will ask you to agree before your first post, comment, message, profile edit, or community upload."
            },
            modifier = Modifier.fillMaxWidth(),
        )
        PirateButton(
            text = "Read Terms of Service",
            onClick = { uriHandler.openUri("$webBaseUrl/terms") },
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        PirateButton(
            text = "Read Privacy Policy",
            onClick = { uriHandler.openUri("$webBaseUrl/privacy") },
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SettingsIndexRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Icon(
            imageVector = PhosphorIcons.CaretRight,
            contentDescription = null,
            tint = PirateTokens.colors.textSecondary,
        )
    }
    HorizontalDivider(color = PirateTokens.colors.borderSoft)
}

@Composable
private fun ProfileSettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    val profile = state.profile ?: return
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectAvatar(uri)
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectCover(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        SettingsSection(title = "Appearance") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                MediaControl(
                    title = "Avatar",
                    shape = MediaShape.Avatar,
                    model = state.avatarModel(profile),
                    selectedLabel = state.pendingAvatarLabel,
                    canRemove = !state.avatarRemoved && (!profile.avatarRef.isNullOrBlank() || state.pendingAvatarUri != null),
                    selectLabel = if (!profile.avatarRef.isNullOrBlank() || state.pendingAvatarUri != null) "Replace avatar" else "Upload avatar",
                    removeLabel = "Remove avatar",
                    onSelect = { avatarPicker.launch("image/*") },
                    onRemove = viewModel::removeAvatar,
                )
                MediaControl(
                    title = "Cover",
                    hint = "1500x500 recommended",
                    shape = MediaShape.Cover,
                    model = state.coverModel(profile),
                    selectedLabel = state.pendingCoverLabel,
                    canRemove = !state.coverRemoved && (!profile.coverRef.isNullOrBlank() || state.pendingCoverUri != null),
                    selectLabel = if (!profile.coverRef.isNullOrBlank() || state.pendingCoverUri != null) "Replace cover" else "Upload cover",
                    removeLabel = "Remove cover",
                    onSelect = { coverPicker.launch("image/*") },
                    onRemove = viewModel::removeCover,
                )
            }
        }

        SettingsSection(title = "Profile") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::updateDisplayName,
                    label = { Text("Display name") },
                    supportingText = {
                        Text("${state.displayName.length}/$DISPLAY_NAME_MAX")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.savingProfile,
                )
                state.displayNameError?.let {
                    FormNote(message = it, tone = FormTone.Error)
                }
                OutlinedTextField(
                    value = state.bio,
                    onValueChange = viewModel::updateBio,
                    label = { Text("Bio") },
                    placeholder = { Text("Tell people about yourself") },
                    supportingText = {
                        Text("${state.bio.length}/$BIO_MAX")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 6,
                    enabled = !state.savingProfile,
                )
                PirateButton(
                    text = "Save profile",
                    onClick = viewModel::saveProfile,
                    enabled = state.profileHasChanges() && !state.savingProfile,
                    loading = state.savingProfile,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SettingsSection(title = "Pirate handle") {
            if (state.handleExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsValueRow("Current handle", state.currentHandleDisplay())
                    OutlinedTextField(
                        value = state.handleLabel.removeSuffix(".pirate"),
                        onValueChange = viewModel::updateHandle,
                        label = { Text("New handle") },
                        placeholder = { Text("your-new-handle") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.renamingHandle,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PirateButton(
                            text = "Rename handle",
                            onClick = viewModel::renameHandle,
                            loading = state.renamingHandle,
                            modifier = Modifier.weight(1f),
                        )
                        PirateButton(
                            text = "Cancel",
                            onClick = { viewModel.setHandleExpanded(false) },
                            variant = ButtonVariant.Outline,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = PirateTokens.colors.bgPage,
                    border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current handle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PirateTokens.colors.textSecondary,
                            )
                            Text(
                                text = state.currentHandleDisplay(),
                                style = MaterialTheme.typography.titleMedium,
                                color = PirateTokens.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        PirateButton(
                            text = "Change",
                            onClick = { viewModel.setHandleExpanded(true) },
                            variant = ButtonVariant.Outline,
                        )
                    }
                }
            }
        }
    }
}

private enum class MediaShape { Avatar, Cover }

@Composable
private fun MediaControl(
    title: String,
    shape: MediaShape,
    model: Any?,
    selectLabel: String,
    removeLabel: String,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    hint: String? = null,
    selectedLabel: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
            )
            hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
            selectedLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (shape == MediaShape.Avatar) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .border(1.dp, PirateTokens.colors.borderSoft, CircleShape)
                    .background(PirateTokens.colors.bgElevated),
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, PirateTokens.colors.borderSoft, RoundedCornerShape(14.dp))
                    .background(PirateTokens.colors.bgElevated),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PirateButton(
                text = selectLabel,
                onClick = onSelect,
                variant = ButtonVariant.Outline,
                modifier = Modifier.weight(1f),
            )
            if (canRemove) {
                PirateButton(
                    text = removeLabel,
                    onClick = onRemove,
                    variant = ButtonVariant.Outline,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = PirateTokens.colors.textPrimary,
        )
        content()
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
    }
}

@Composable
private fun PreferencesSettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        SettingsSection(title = "Appearance") {
            Text(
                text = "Use your device setting or choose a theme for Pirate.",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceMode.entries.forEach { mode ->
                    PirateButton(
                        text = mode.displayLabel(),
                        onClick = { viewModel.setAppearanceMode(mode) },
                        variant = if (state.appearanceMode == mode) ButtonVariant.Default else ButtonVariant.Outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        SettingsSection(title = "Language") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = state.preferredLocale,
                    onValueChange = viewModel::updatePreferredLocale,
                    label = { Text("App language") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.savingPreferences,
                )
                PirateButton(
                    text = "Save preferences",
                    onClick = viewModel::savePreferences,
                    loading = state.savingPreferences,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun AppearanceMode.displayLabel(): String = when (this) {
    AppearanceMode.System -> "System"
    AppearanceMode.Light -> "Light"
    AppearanceMode.Dark -> "Dark"
}

@Composable
private fun DomainsSettings() {
    StatusCard(
        title = "Domains are not wired on Android yet",
        description = "Handle and domain controls remain web-only for this v0.",
        tone = StatusTone.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AgentsSettings() {
    AgentsSettingsPanel()
}

private fun SettingsUiState.profileHasChanges(): Boolean {
    val profile = profile ?: return false
    return displayName.trim() != profile.displayName.orEmpty().trim() ||
        bio != profile.bio.orEmpty() ||
        pendingAvatarUri != null ||
        pendingCoverUri != null ||
        (avatarRemoved && !profile.avatarRef.isNullOrBlank()) ||
        (coverRemoved && !profile.coverRef.isNullOrBlank())
}

private fun SettingsUiState.currentHandleDisplay(): String {
    val label = handleLabel.ifBlank { profile?.displayHandle().orEmpty() }
    if (label.isBlank()) return ""
    return if (label.contains(".")) label else "$label.pirate"
}

private fun SettingsUiState.avatarModel(profile: Profile): Any? {
    if (pendingAvatarUri != null) return pendingAvatarUri
    val seed = profile.userId.ifBlank { currentHandleDisplay().ifBlank { displayName } }
    return if (avatarRemoved) {
        buildDefaultUserAvatarSrc(seed)
    } else {
        resolvePublicMediaSrc(profile.avatarRef) ?: buildDefaultUserAvatarSrc(seed)
    }
}

private fun SettingsUiState.coverModel(profile: Profile): Any? {
    if (pendingCoverUri != null) return pendingCoverUri
    val displayHandle = profile.displayHandle()
    val displayName = profile.displayName ?: displayHandle.ifBlank { "Profile" }
    val seed = profile.userId.ifBlank { displayHandle.ifBlank { displayName } }
    return if (coverRemoved) {
        buildDefaultProfileCoverSrc(displayName = displayName, handle = displayHandle, userId = seed)
    } else {
        resolvePublicMediaSrc(profile.coverRef)
            ?: buildDefaultProfileCoverSrc(displayName = displayName, handle = displayHandle, userId = seed)
    }
}
