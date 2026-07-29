package sc.pirate.app.createcommunity

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.CreateCommunityBootstrapInput
import sc.pirate.app.api.model.CreateCommunityRequest
import sc.pirate.app.api.model.CreateCommunityRuleInput
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import java.util.Locale

data class CreateCommunityUiState(
    val step: CreateCommunityStep = CreateCommunityStep.Basics,
    val displayName: String = "",
    val description: String = "",
    val databaseRegion: String = "aws-us-east-1",
    val avatarUri: Uri? = null,
    val avatarLabel: String? = null,
    val bannerUri: Uri? = null,
    val bannerLabel: String? = null,
    val membershipMode: CommunityMembershipMode = CommunityMembershipMode.Gated,
    val gateMatchMode: CommunityGateMatchMode = CommunityGateMatchMode.All,
    val gateDrafts: List<IdentityGateDraft> = defaultGatedGateDrafts,
    val defaultAgeGatePolicy: CommunityDefaultAgeGatePolicy = CommunityDefaultAgeGatePolicy.None,
    val allowAnonymousIdentity: Boolean = true,
    val anonymousIdentityScope: AnonymousIdentityScope = AnonymousIdentityScope.CommunityStable,
    val creatorAgeOver18Verified: Boolean = false,
    val pendingCreateAfterAgeVerification: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val createdCommunityId: String? = null,
    val provisioningStatus: String? = null,
) {
    val effectiveAgeGatePolicy: CommunityDefaultAgeGatePolicy
        get() = effectiveDefaultAgeGatePolicy(
            membershipMode = membershipMode,
            defaultAgeGatePolicy = defaultAgeGatePolicy,
            gateDrafts = gateDrafts,
        )

    val canProceed: Boolean
        get() = canAdvanceCreateCommunityStep(
            step = step,
            displayName = displayName,
            membershipMode = membershipMode,
            gateDrafts = gateDrafts,
        )
}

class CreateCommunityViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val contentResolver get() = app.contentResolver

    private val _state = MutableStateFlow(CreateCommunityUiState())
    val state: StateFlow<CreateCommunityUiState> = _state.asStateFlow()

    fun updateDisplayName(value: String) {
        _state.value = _state.value.copy(displayName = value, error = null)
    }

    fun updateDescription(value: String) {
        _state.value = _state.value.copy(description = value, error = null)
    }

    fun selectDatabaseRegion(value: String) {
        if (createCommunityDatabaseRegions.none { it.value == value }) return
        _state.value = _state.value.copy(databaseRegion = value, error = null)
    }

    fun selectAvatar(uri: Uri?) {
        _state.value = _state.value.copy(
            avatarUri = uri,
            avatarLabel = uri?.displayName(),
            error = null,
        )
    }

    fun selectBanner(uri: Uri?) {
        _state.value = _state.value.copy(
            bannerUri = uri,
            bannerLabel = uri?.displayName(),
            error = null,
        )
    }

    fun removeAvatar() {
        _state.value = _state.value.copy(avatarUri = null, avatarLabel = null, error = null)
    }

    fun removeBanner() {
        _state.value = _state.value.copy(bannerUri = null, bannerLabel = null, error = null)
    }

    fun selectMembershipMode(value: CommunityMembershipMode) {
        val current = _state.value
        _state.value = current.copy(
            membershipMode = value,
            gateDrafts = if (value == CommunityMembershipMode.Request) {
                emptyList()
            } else if (current.gateDrafts.isEmpty()) {
                defaultGatedGateDrafts
            } else {
                current.gateDrafts
            },
            gateMatchMode = if (value == CommunityMembershipMode.Request) {
                CommunityGateMatchMode.All
            } else {
                current.gateMatchMode
            },
            error = null,
        )
    }

    fun selectGateMatchMode(value: CommunityGateMatchMode) {
        val current = _state.value
        _state.value = current.copy(
            gateMatchMode = value,
            gateDrafts = normalizeGateDraftsForMatchMode(current.gateDrafts, value),
            error = null,
        )
    }

    fun upsertGate(draft: IdentityGateDraft) {
        val current = _state.value
        _state.value = current.copy(
            gateDrafts = upsertGateDraftForMatchMode(current.gateDrafts, draft, current.gateMatchMode),
            error = null,
        )
    }

    fun removeGate(gateType: String) {
        val current = _state.value
        val removingPalmScanFallback =
            gateType == "unique_human" &&
                current.gateMatchMode == CommunityGateMatchMode.Any &&
                current.gateDrafts.any { it.gateType == "altcha_pow" }
        val resetAfterPowRemoval = shouldResetMatchModeAfterRemovingPowFallback(
            drafts = current.gateDrafts,
            gateMatchMode = current.gateMatchMode,
        )
        _state.value = current.copy(
            gateMatchMode = if (
                (gateType == "altcha_pow" && resetAfterPowRemoval) ||
                removingPalmScanFallback
            ) {
                CommunityGateMatchMode.All
            } else {
                current.gateMatchMode
            },
            gateDrafts = removeGateDraft(current.gateDrafts, gateType),
            error = null,
        )
    }

    fun setPalmScanPowFallbackEnabled(enabled: Boolean) {
        val current = _state.value
        if (enabled) {
            _state.value = current.copy(
                gateMatchMode = CommunityGateMatchMode.Any,
                gateDrafts = upsertGateDraftForMatchMode(
                    drafts = current.gateDrafts,
                    nextDraft = AltchaPowGateDraft,
                    gateMatchMode = CommunityGateMatchMode.Any,
                ),
                error = null,
            )
            return
        }
        val shouldReset = shouldResetMatchModeAfterRemovingPowFallback(
            drafts = current.gateDrafts,
            gateMatchMode = current.gateMatchMode,
        )
        _state.value = current.copy(
            gateMatchMode = if (shouldReset) CommunityGateMatchMode.All else current.gateMatchMode,
            gateDrafts = removeGateDraft(current.gateDrafts, "altcha_pow"),
            error = null,
        )
    }

    fun setDefaultAgeGatePolicy(value: CommunityDefaultAgeGatePolicy) {
        _state.value = _state.value.copy(defaultAgeGatePolicy = value, error = null)
    }

    fun setAllowAnonymousIdentity(value: Boolean) {
        _state.value = _state.value.copy(allowAnonymousIdentity = value, error = null)
    }

    fun setAnonymousIdentityScope(value: AnonymousIdentityScope) {
        _state.value = _state.value.copy(anonymousIdentityScope = value, error = null)
    }

    fun nextStep() {
        val current = _state.value
        if (!current.canProceed) return
        _state.value = current.copy(
            step = when (current.step) {
                CreateCommunityStep.Basics -> CreateCommunityStep.Access
                CreateCommunityStep.Access -> CreateCommunityStep.Review
                CreateCommunityStep.Review -> CreateCommunityStep.Review
            },
            error = null,
        )
    }

    fun previousStep() {
        val current = _state.value
        _state.value = current.copy(
            step = when (current.step) {
                CreateCommunityStep.Basics -> CreateCommunityStep.Basics
                CreateCommunityStep.Access -> CreateCommunityStep.Basics
                CreateCommunityStep.Review -> CreateCommunityStep.Access
            },
            error = null,
        )
    }

    fun submit(onAgeVerificationRequired: () -> Unit) {
        val current = _state.value
        if (!current.canProceed) {
            _state.value = current.copy(error = "Finish the required fields before creating this community.")
            return
        }
        if (
            current.effectiveAgeGatePolicy == CommunityDefaultAgeGatePolicy.EighteenPlus &&
            !current.creatorAgeOver18Verified
        ) {
            _state.value = current.copy(
                pendingCreateAfterAgeVerification = true,
                error = null,
            )
            onAgeVerificationRequired()
            return
        }
        submitCurrentState()
    }

    fun markCreatorAgeVerifiedAndSubmitPending() {
        val current = _state.value
        val next = current.copy(
            creatorAgeOver18Verified = true,
            error = null,
        )
        _state.value = next
        if (next.pendingCreateAfterAgeVerification) {
            submitCurrentState()
        }
    }

    private fun submitCurrentState() {
        val current = _state.value
        val displayName = current.displayName.trim()
        if (displayName.isBlank()) {
            _state.value = current.copy(error = "Display name is required.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            if (!app.termsAcceptanceManager.requireForUgc()) {
                _state.value = _state.value.copy(submitting = false)
                return@launch
            }
            try {
                val avatarRef = current.avatarUri?.uploadCommunityMedia("avatar")
                val bannerRef = current.bannerUri?.uploadCommunityMedia("banner")
                val result = communityRepository.createCommunity(
                    current.toCreateRequest(
                        displayName = displayName,
                        avatarRef = avatarRef,
                        bannerRef = bannerRef,
                    ),
                )
                app.knownCommunitiesStore.remember(
                    communityId = result.community.communityId,
                    displayName = result.community.displayName,
                    avatarRef = result.community.avatarRef,
                    routeSlug = result.community.routeSlug,
                )
                _state.value = _state.value.copy(
                    submitting = false,
                    pendingCreateAfterAgeVerification = false,
                    createdCommunityId = result.community.communityId,
                    provisioningStatus = result.job.status,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    submitting = false,
                    error = e.message ?: "Community creation failed",
                )
            }
        }
    }

    private fun CreateCommunityUiState.toCreateRequest(
        displayName: String,
        avatarRef: String?,
        bannerRef: String?,
    ): CreateCommunityRequest {
        val activeGateDrafts = if (membershipMode == CommunityMembershipMode.Gated) gateDrafts else emptyList()
        return CreateCommunityRequest(
            avatarRef = avatarRef,
            bannerRef = bannerRef,
            displayName = displayName,
            databaseRegion = databaseRegion,
            description = description.trim().ifBlank { null },
            membershipMode = membershipMode.value,
            defaultAgeGatePolicy = effectiveAgeGatePolicy.value,
            allowAnonymousIdentity = allowAnonymousIdentity,
            anonymousIdentityScope = anonymousIdentityScope.value,
            gatePolicy = serializeIdentityGateDrafts(activeGateDrafts, gateMatchMode),
            communityBootstrap = CreateCommunityBootstrapInput(rules = defaultBootstrapRules()),
        )
    }

    private fun defaultBootstrapRules(): List<CreateCommunityRuleInput> = listOf(
        CreateCommunityRuleInput(
            title = "Respect others and be civil",
            body = "No harassment, hate speech, or toxic behavior. Treat all contributors and members with kindness.",
            reportReason = "Respect others and be civil",
            position = 0,
        ),
        CreateCommunityRuleInput(
            title = "No spam",
            body = "Excessive promotion, spam, or advertising of any kind is not allowed.",
            reportReason = "No spam",
            position = 1,
        ),
    )

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

    private suspend fun Uri.uploadCommunityMedia(kind: String): String {
        val mimeType = contentResolver.getType(this) ?: "image/jpeg"
        val name = displayName()
        val bytes = contentResolver.openInputStream(this)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read selected image.")
        return communityRepository.uploadMedia(kind, bytes, name, mimeType)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityScreen(
    onBack: () -> Unit,
    onVerifyAge: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
    ageVerificationCompleted: Boolean = false,
    onAgeVerificationConsumed: () -> Unit = {},
) {
    val viewModel: CreateCommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectAvatar(uri)
    }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectBanner(uri)
    }

    LaunchedEffect(state.createdCommunityId) {
        state.createdCommunityId?.let(onCreated)
    }

    LaunchedEffect(ageVerificationCompleted) {
        if (ageVerificationCompleted) {
            viewModel.markCreatorAgeVerifiedAndSubmitPending()
            onAgeVerificationConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Create community", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PhosphorIcons.CaretLeft,
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
        bottomBar = {
            CreateCommunityBottomBar(
                state = state,
                onBack = viewModel::previousStep,
                onCreate = { viewModel.submit(onAgeVerificationRequired = onVerifyAge) },
                onNext = viewModel::nextStep,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                when (state.step) {
                    CreateCommunityStep.Basics -> CreateCommunityBasicsStep(
                        state = state,
                        onAvatarSelect = { avatarPicker.launch("image/*") },
                        onAvatarRemove = viewModel::removeAvatar,
                        onBannerSelect = { bannerPicker.launch("image/*") },
                        onBannerRemove = viewModel::removeBanner,
                        onDatabaseRegionChange = viewModel::selectDatabaseRegion,
                        onDescriptionChange = viewModel::updateDescription,
                        onDisplayNameChange = viewModel::updateDisplayName,
                    )
                    CreateCommunityStep.Access -> CreateCommunityAccessStep(
                        state = state,
                        viewModel = viewModel,
                    )
                    CreateCommunityStep.Review -> CreateCommunityReviewStep(state = state)
                }
            }
            item {
                state.error?.let {
                    FormNote(
                        message = it,
                        tone = FormTone.Error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCommunityBasicsStep(
    state: CreateCommunityUiState,
    onAvatarSelect: () -> Unit,
    onAvatarRemove: () -> Unit,
    onBannerSelect: () -> Unit,
    onBannerRemove: () -> Unit,
    onDatabaseRegionChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display name") },
                placeholder = { Text("Community name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.submitting,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description") },
                placeholder = { Text("What is this community for?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                enabled = !state.submitting,
            )
            DatabaseRegionSelector(
                selected = state.databaseRegion,
                onSelected = onDatabaseRegionChange,
            )
        }

        FlatSection(title = "Images") {
            MediaPickerRow(
                title = "Avatar",
                label = state.avatarLabel,
                model = state.avatarUri,
                onRemove = onAvatarRemove,
                onSelect = onAvatarSelect,
            )
            MediaPickerRow(
                title = "Banner",
                label = state.bannerLabel,
                model = state.bannerUri,
                onRemove = onBannerRemove,
                onSelect = onBannerSelect,
                wide = true,
            )
        }
    }
}

@Composable
private fun CreateCommunityAccessStep(
    state: CreateCommunityUiState,
    viewModel: CreateCommunityViewModel,
) {
    val uniqueHumanGate = state.gateDrafts.filterIsInstance<UniqueHumanGateDraft>().firstOrNull()
    val nationalityGate = state.gateDrafts.filterIsInstance<NationalityGateDraft>().firstOrNull()
    val minimumAgeGate = state.gateDrafts.filterIsInstance<MinimumAgeGateDraft>().firstOrNull()
    val genderGate = state.gateDrafts.filterIsInstance<GenderGateDraft>().firstOrNull()
    val walletScoreGate = state.gateDrafts.filterIsInstance<WalletScoreGateDraft>().firstOrNull()
    val erc721Gate = state.gateDrafts.filterIsInstance<Erc721HoldingGateDraft>().firstOrNull()
    val hasAltchaPowGate = state.gateDrafts.any { it.gateType == "altcha_pow" }
    val palmScanPowFallbackEnabled =
        uniqueHumanGate != null &&
            hasAltchaPowGate &&
            state.gateMatchMode == CommunityGateMatchMode.Any
    val hasAdultMinimumAgeGate =
        minimumAgeGate != null &&
            minimumAgeGate.minimumAge in 18..125

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FlatSection(title = "Join policy") {
            OptionRow(
                title = "Approval required",
                description = "People request access and moderators approve them before they can join.",
                selected = state.membershipMode == CommunityMembershipMode.Request,
                onClick = { viewModel.selectMembershipMode(CommunityMembershipMode.Request) },
            )
            OptionRow(
                title = "Automatic after passing gates",
                description = "People can join after passing at least one wallet, identity, or ownership gate.",
                selected = state.membershipMode == CommunityMembershipMode.Gated,
                onClick = { viewModel.selectMembershipMode(CommunityMembershipMode.Gated) },
            )
        }

        if (state.membershipMode == CommunityMembershipMode.Gated) {
            FlatSection(title = "Gate logic") {
                OptionRow(
                    title = "All selected gates",
                    description = "Members must pass every selected gate.",
                    selected = state.gateMatchMode == CommunityGateMatchMode.All,
                    onClick = { viewModel.selectGateMatchMode(CommunityGateMatchMode.All) },
                )
                OptionRow(
                    title = "Any selected gate",
                    description = "Members can pass any one selected gate.",
                    selected = state.gateMatchMode == CommunityGateMatchMode.Any,
                    onClick = { viewModel.selectGateMatchMode(CommunityGateMatchMode.Any) },
                )
            }

            FlatSection(title = "Identity gates") {
                ToggleRow(
                    checked = hasAltchaPowGate,
                    title = "Proof-of-work check",
                    onCheckedChange = { checked ->
                        if (checked) viewModel.upsertGate(AltchaPowGateDraft) else viewModel.removeGate("altcha_pow")
                    },
                )
                ToggleRow(
                    checked = uniqueHumanGate != null,
                    title = "Palm scan (Very)",
                    onCheckedChange = { checked ->
                        if (checked) viewModel.upsertGate(UniqueHumanGateDraft()) else viewModel.removeGate("unique_human")
                    },
                )
                if (uniqueHumanGate != null) {
                    OptionRow(
                        title = "Allow proof-of-work fallback",
                        description = "Members can join by completing either the palm scan or the proof-of-work check.",
                        selected = palmScanPowFallbackEnabled,
                        onClick = { viewModel.setPalmScanPowFallbackEnabled(!palmScanPowFallbackEnabled) },
                    )
                }
                ToggleRow(
                    checked = nationalityGate != null,
                    title = "Nationality verification (Self.xyz)",
                    onCheckedChange = { checked ->
                        if (checked) viewModel.upsertGate(NationalityGateDraft()) else viewModel.removeGate("nationality")
                    },
                )
                if (nationalityGate != null) {
                    OutlinedTextField(
                        value = nationalityGate.requiredValues.joinToString(", "),
                        onValueChange = { value ->
                            viewModel.upsertGate(
                                NationalityGateDraft(
                                    requiredValues = value
                                        .split(',', ' ', ';')
                                        .map { it.trim().uppercase(Locale.ROOT) }
                                        .filter { it.isNotBlank() },
                                ),
                            )
                        },
                        label = { Text("Allowed nationalities") },
                        placeholder = { Text("US, CA") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                ToggleRow(
                    checked = minimumAgeGate != null,
                    title = "Minimum age (Self.xyz)",
                    onCheckedChange = { checked ->
                        if (checked) viewModel.upsertGate(MinimumAgeGateDraft()) else viewModel.removeGate("minimum_age")
                    },
                )
                if (minimumAgeGate != null) {
                    NumericStepper(
                        label = "Minimum age",
                        value = minimumAgeGate.minimumAge,
                        min = 18,
                        max = 125,
                        onChange = { viewModel.upsertGate(MinimumAgeGateDraft(minimumAge = it)) },
                    )
                }
                ToggleRow(
                    checked = genderGate != null,
                    title = "Document sex marker (verified ID)",
                    onCheckedChange = { checked ->
                        if (checked) viewModel.upsertGate(GenderGateDraft()) else viewModel.removeGate("gender")
                    },
                )
                if (genderGate != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OptionRow(
                            title = "F marker",
                            selected = genderGate.requiredValue == "F",
                            onClick = { viewModel.upsertGate(GenderGateDraft(requiredValue = "F")) },
                            modifier = Modifier.weight(1f),
                        )
                        OptionRow(
                            title = "M marker",
                            selected = genderGate.requiredValue == "M",
                            onClick = { viewModel.upsertGate(GenderGateDraft(requiredValue = "M")) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            FlatSection(title = "Wallet gates") {
                ToggleRow(
                    checked = walletScoreGate != null,
                    title = "Passport score threshold",
                    onCheckedChange = { checked ->
                        if (checked) viewModel.upsertGate(WalletScoreGateDraft()) else viewModel.removeGate("wallet_score")
                    },
                )
                if (walletScoreGate != null) {
                    NumericStepper(
                        label = "Minimum score",
                        value = walletScoreGate.minimumScore,
                        min = 0,
                        max = 100,
                        onChange = { viewModel.upsertGate(WalletScoreGateDraft(minimumScore = it)) },
                    )
                }
                ToggleRow(
                    checked = erc721Gate != null,
                    title = "Ethereum NFT collection (ERC-721)",
                    onCheckedChange = { checked ->
                        if (checked) viewModel.upsertGate(Erc721HoldingGateDraft()) else viewModel.removeGate("erc721_holding")
                    },
                )
                if (erc721Gate != null) {
                    OutlinedTextField(
                        value = erc721Gate.contractAddress,
                        onValueChange = { viewModel.upsertGate(Erc721HoldingGateDraft(contractAddress = it)) },
                        label = { Text("Collection contract") },
                        placeholder = { Text("0x...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    invalidGateDraftReason(erc721Gate)?.let {
                        FormNote(message = "Enter a valid Ethereum contract address.", tone = FormTone.Warning)
                    }
                }
                ToggleRow(
                    checked = false,
                    enabled = false,
                    title = "Courtyard.io collectibles (Coming soon)",
                    onCheckedChange = {},
                )
            }
        }

        if (!hasAdultMinimumAgeGate) {
            FlatSection(title = "Age restriction") {
                ToggleRow(
                    checked = state.defaultAgeGatePolicy == CommunityDefaultAgeGatePolicy.EighteenPlus,
                    title = "18+ community",
                    onCheckedChange = { checked ->
                        viewModel.setDefaultAgeGatePolicy(
                            if (checked) CommunityDefaultAgeGatePolicy.EighteenPlus else CommunityDefaultAgeGatePolicy.None,
                        )
                    },
                )
            }
        }

        FlatSection(title = "Identity and access") {
            ToggleRow(
                checked = state.allowAnonymousIdentity,
                title = "Allow anonymous posting",
                onCheckedChange = viewModel::setAllowAnonymousIdentity,
            )
            if (state.allowAnonymousIdentity) {
                Text(
                    text = "Anonymous scope",
                    style = MaterialTheme.typography.titleMedium,
                    color = PirateTokens.colors.textPrimary,
                )
                OptionRow(
                    title = "Community-stable",
                    description = "One persistent anonymous label per user across the community.",
                    selected = state.anonymousIdentityScope == AnonymousIdentityScope.CommunityStable,
                    onClick = { viewModel.setAnonymousIdentityScope(AnonymousIdentityScope.CommunityStable) },
                )
                OptionRow(
                    title = "Thread-stable",
                    description = "One persistent anonymous label per user per thread.",
                    selected = state.anonymousIdentityScope == AnonymousIdentityScope.ThreadStable,
                    onClick = { viewModel.setAnonymousIdentityScope(AnonymousIdentityScope.ThreadStable) },
                )
            }
        }
    }
}

@Composable
private fun CreateCommunityReviewStep(state: CreateCommunityUiState) {
    val gateSummary = if (state.membershipMode == CommunityMembershipMode.Gated) {
        formatGateRequirementList(state.gateDrafts, state.gateMatchMode)
    } else {
        null
    }
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        ReviewGroup {
            ReviewRow(label = "Display name", value = state.displayName.trim())
            ReviewRow(label = "Description", value = state.description.trim().ifBlank { "-" })
            ReviewRow(label = "Data region", value = createCommunityDatabaseRegionLabel(state.databaseRegion))
            ReviewRow(label = "Avatar", value = state.avatarLabel ?: "Generated default")
            ReviewRow(label = "Banner", value = state.bannerLabel ?: "Generated default")
        }
        ReviewGroup(title = "Access policy") {
            ReviewRow(label = "Join flow", value = formatMembershipLabel(state.membershipMode))
            ReviewRow(
                label = "Content rating",
                value = if (state.effectiveAgeGatePolicy == CommunityDefaultAgeGatePolicy.EighteenPlus) "18+" else "All ages",
            )
            if (gateSummary != null) {
                ReviewRow(label = "Membership gates", value = gateSummary)
            }
            ReviewRow(
                label = "Anonymous posting",
                value = if (state.allowAnonymousIdentity) "Enabled" else "Disabled",
            )
            if (state.allowAnonymousIdentity) {
                ReviewRow(label = "Anonymous scope", value = formatAnonymousScopeLabel(state.anonymousIdentityScope))
            }
        }
    }
}

@Composable
private fun FlatSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = PirateTokens.colors.textPrimary,
        )
        content()
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(PirateTokens.radius.lg),
        color = if (selected) PirateTokens.colors.surfaceInteractive else PirateTokens.colors.bgPage,
        border = BorderStroke(1.dp, if (selected) PirateTokens.colors.borderStrong else PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) PirateTokens.colors.textPrimary else PirateTokens.colors.textDisabled,
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    checked: Boolean,
    title: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier),
        shape = RoundedCornerShape(PirateTokens.radius.lg),
        color = if (checked) PirateTokens.colors.surfaceInteractive else PirateTokens.colors.bgPage,
        border = BorderStroke(1.dp, if (checked) PirateTokens.colors.borderStrong else PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) PirateTokens.colors.textPrimary else PirateTokens.colors.textDisabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NumericStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PirateButton(
                text = "-",
                variant = ButtonVariant.Outline,
                enabled = value > min,
                onClick = { onChange((value - 1).coerceAtLeast(min)) },
                modifier = Modifier.width(56.dp),
            )
            OutlinedTextField(
                value = value.toString(),
                onValueChange = { next ->
                    next.toIntOrNull()?.let { onChange(it.coerceIn(min, max)) }
                },
                modifier = Modifier.width(96.dp),
                singleLine = true,
            )
            PirateButton(
                text = "+",
                variant = ButtonVariant.Outline,
                enabled = value < max,
                onClick = { onChange((value + 1).coerceAtMost(max)) },
                modifier = Modifier.width(56.dp),
            )
        }
    }
}

@Composable
private fun DatabaseRegionSelector(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Data region",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(PirateTokens.radius.lg),
                color = PirateTokens.colors.bgPage,
                border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = createCommunityDatabaseRegionLabel(selected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = PirateTokens.colors.textPrimary,
                    )
                    Icon(
                        imageVector = PhosphorIcons.CaretDown,
                        contentDescription = null,
                        tint = PirateTokens.colors.textSecondary,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                createCommunityDatabaseRegions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onSelected(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPickerRow(
    title: String,
    label: String?,
    model: Any?,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    wide: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (wide) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 56.dp)
                    .clip(RoundedCornerShape(PirateTokens.radius.lg))
                    .border(1.dp, PirateTokens.colors.borderSoft, RoundedCornerShape(PirateTokens.radius.lg))
                    .background(PirateTokens.colors.bgElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = PhosphorIcons.ImageSquare,
                        contentDescription = null,
                        tint = PirateTokens.colors.textSecondary,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(1.dp, PirateTokens.colors.borderSoft, CircleShape)
                    .background(PirateTokens.colors.bgElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (model != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = PhosphorIcons.ImageSquare,
                        contentDescription = null,
                        tint = PirateTokens.colors.textSecondary,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
            )
            Text(
                text = label ?: "No file selected",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PirateButton(
                text = if (model == null) "Choose" else "Replace",
                onClick = onSelect,
                variant = ButtonVariant.Outline,
            )
            if (model != null) {
                PirateButton(
                    text = "Remove",
                    onClick = onRemove,
                    variant = ButtonVariant.Outline,
                )
            }
        }
    }
}

@Composable
private fun ReviewGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.headlineSmall,
                color = PirateTokens.colors.textPrimary,
            )
        }
        content()
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
    }
}

@Composable
private fun ReviewRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
    }
}

@Composable
private fun CreateCommunityBottomBar(
    state: CreateCommunityUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        color = PirateTokens.colors.bgPage,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.step != CreateCommunityStep.Basics) {
                PirateButton(
                    text = "Back",
                    onClick = onBack,
                    variant = ButtonVariant.Outline,
                    enabled = !state.submitting,
                    modifier = Modifier.weight(0.45f),
                )
            }
            PirateButton(
                text = if (state.step == CreateCommunityStep.Review) "Create Community" else "Next",
                onClick = if (state.step == CreateCommunityStep.Review) onCreate else onNext,
                loading = state.submitting,
                enabled = state.canProceed && !state.submitting,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
