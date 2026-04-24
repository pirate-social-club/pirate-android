package sc.pirate.app.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ProfileUpdateInput
import sc.pirate.app.api.model.Profile
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class SettingsUiState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val displayName: String = "",
    val bio: String = "",
    val handleLabel: String = "",
    val preferredLocale: String = "",
    val savingProfile: Boolean = false,
    val savingPreferences: Boolean = false,
    val renamingHandle: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, message = null)
            try {
                val profile = profileRepository.getMe()
                _state.value = SettingsUiState(
                    loading = false,
                    profile = profile,
                    displayName = profile.displayName.orEmpty(),
                    bio = profile.bio.orEmpty(),
                    handleLabel = profile.globalHandle?.label.orEmpty(),
                    preferredLocale = profile.preferredLocale.orEmpty(),
                )
            } catch (e: Exception) {
                _state.value = SettingsUiState(
                    loading = false,
                    error = e.message ?: "Could not load settings",
                )
            }
        }
    }

    fun updateDisplayName(value: String) {
        _state.value = _state.value.copy(displayName = value, error = null, message = null)
    }

    fun updateBio(value: String) {
        _state.value = _state.value.copy(bio = value, error = null, message = null)
    }

    fun updateHandle(value: String) {
        _state.value = _state.value.copy(handleLabel = value, error = null, message = null)
    }

    fun updatePreferredLocale(value: String) {
        _state.value = _state.value.copy(preferredLocale = value, error = null, message = null)
    }

    fun saveProfile() {
        viewModelScope.launch {
            val current = _state.value
            _state.value = current.copy(savingProfile = true, error = null, message = null)
            try {
                val profile = profileRepository.updateMe(
                    ProfileUpdateInput(
                        displayName = current.displayName.trim().ifBlank { null },
                        bio = current.bio.trim().ifBlank { null },
                    ),
                )
                _state.value = _state.value.copy(
                    profile = profile,
                    displayName = profile.displayName.orEmpty(),
                    bio = profile.bio.orEmpty(),
                    preferredLocale = profile.preferredLocale.orEmpty(),
                    savingProfile = false,
                    message = "Profile updated.",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    savingProfile = false,
                    error = e.message ?: "Could not save profile",
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
                    message = "Preferences updated.",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    savingPreferences = false,
                    error = e.message ?: "Could not save preferences",
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
                    message = "Handle updated.",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    renamingHandle = false,
                    error = e.message ?: "Could not rename handle",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    section: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
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
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            text = section.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.headlineSmall,
                            color = PirateTokens.colors.textPrimary,
                        )
                    }
                    if (state.message != null) {
                        item {
                            StatusCard(
                                title = state.message.orEmpty(),
                                description = "Your account settings are up to date.",
                                tone = StatusTone.Success,
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
                        "agents" -> item { AgentsSettings() }
                        else -> item { ProfileSettings(state, viewModel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::updateDisplayName,
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.savingProfile,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.bio,
            onValueChange = viewModel::updateBio,
            label = { Text("Bio") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            enabled = !state.savingProfile,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PirateButton(
            text = "Save profile",
            onClick = viewModel::saveProfile,
            loading = state.savingProfile,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Public handle",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.handleLabel,
                onValueChange = viewModel::updateHandle,
                label = { Text("Handle") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !state.renamingHandle,
            )
            PirateButton(
                text = "Rename",
                onClick = viewModel::renameHandle,
                loading = state.renamingHandle,
            )
        }
    }
}

@Composable
private fun PreferencesSettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Preferences",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.preferredLocale,
            onValueChange = viewModel::updatePreferredLocale,
            label = { Text("Preferred locale") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.savingPreferences,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PirateButton(
            text = "Save preferences",
            onClick = viewModel::savePreferences,
            loading = state.savingPreferences,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AgentsSettings() {
    StatusCard(
        title = "Agents are not wired on Android yet",
        description = "Agent registration and ownership controls remain web-only for this v0.",
        tone = StatusTone.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}
