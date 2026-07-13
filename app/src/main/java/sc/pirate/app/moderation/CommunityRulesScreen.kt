package sc.pirate.app.moderation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityRulesScreen(
    communityId: String,
    onBack: () -> Unit,
    onOpenRequests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CommunityModerationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.rulesState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val validationError = communityRulesValidationError(state.rules)

    LaunchedEffect(communityId) {
        viewModel.loadRules(communityId)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearRulesMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Rules", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PhosphorIcons.CaretLeft,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenRequests) {
                        Text("Requests", color = PirateTokens.colors.textPrimary)
                    }
                    TextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.addRule()
                        },
                        enabled = !state.loading && !state.saving,
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Plus,
                            contentDescription = null,
                            tint = PirateTokens.colors.textPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(" Add rule", color = PirateTokens.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
        bottomBar = {
            if (!state.loading && !(state.error != null && state.rules.isEmpty())) {
                Surface(
                    color = PirateTokens.colors.bgPage,
                    shadowElevation = 8.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (validationError != null && state.rules.isNotEmpty()) {
                            FormNote(message = validationError, tone = FormTone.Error)
                        }
                        PirateButton(
                            text = "Save rules",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.saveRules(communityId)
                            },
                            enabled = validationError == null && state.hasChanges(),
                            loading = state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            state.loading -> CommunityRulesSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            state.error != null && state.rules.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusCard(
                        title = "Rules unavailable",
                        description = state.error.orEmpty(),
                        tone = StatusTone.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PirateButton(
                        text = "Try again",
                        onClick = { viewModel.loadRules(communityId) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Set the standard",
                                style = MaterialTheme.typography.titleLarge,
                                color = PirateTokens.colors.textPrimary,
                            )
                            Text(
                                text = "Rules appear in order on the community page and can power report reasons.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PirateTokens.colors.textSecondary,
                            )
                        }
                    }

                    state.error?.let { error ->
                        item {
                            StatusCard(
                                title = "Rules not saved",
                                description = error,
                                tone = StatusTone.Warning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (state.rules.isEmpty()) {
                        item {
                            StatusCard(
                                title = "No rules yet",
                                description = "Add the first rule to make expectations clear.",
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        state.rules.forEachIndexed { index, rule ->
                            item(key = rule.id) {
                                CommunityRuleEditorCard(
                                    index = index,
                                    totalRules = state.rules.size,
                                    rule = rule,
                                    enabled = !state.saving,
                                    onUpdate = { update -> viewModel.updateRule(rule.id, update) },
                                    onMoveUp = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.moveRule(index, index - 1)
                                    },
                                    onMoveDown = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.moveRule(index, index + 1)
                                    },
                                    onRemove = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.removeRule(rule.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityRuleEditorCard(
    index: Int,
    totalRules: Int,
    rule: CommunityRuleDraft,
    enabled: Boolean,
    onUpdate: ((CommunityRuleDraft) -> CommunityRuleDraft) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Rule ${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMoveUp, enabled = enabled && index > 0) {
                Icon(
                    imageVector = PhosphorIcons.CaretUp,
                    contentDescription = "Move rule up",
                    tint = PirateTokens.colors.textSecondary,
                )
            }
            IconButton(onClick = onMoveDown, enabled = enabled && index < totalRules - 1) {
                Icon(
                    imageVector = PhosphorIcons.CaretDown,
                    contentDescription = "Move rule down",
                    tint = PirateTokens.colors.textSecondary,
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = PhosphorIcons.X,
                    contentDescription = "Delete rule",
                    tint = PirateTokens.colors.accentDanger,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = rule.title,
            onValueChange = { value -> onUpdate { it.copy(title = value) } },
            label = { Text("Rule title") },
            supportingText = { Text("${rule.title.length}/100") },
            isError = rule.title.isBlank() || rule.title.length > 100,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = rule.body,
            onValueChange = { value -> onUpdate { it.copy(body = value) } },
            label = { Text("Description") },
            supportingText = { Text("${rule.body.length}/500") },
            isError = rule.body.length > 500,
            enabled = enabled,
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = rule.reportReason,
            onValueChange = { value -> onUpdate { it.copy(reportReason = value) } },
            label = { Text("Report reason") },
            placeholder = { Text("Uses the rule title when blank") },
            supportingText = { Text("${rule.reportReason.length}/100") },
            isError = rule.reportReason.length > 100,
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CommunityRulesSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            PirateCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PirateTokens.colors.surfaceInteractive),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PirateTokens.colors.surfaceInteractive),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PirateTokens.colors.surfaceInteractive),
                )
            }
        }
    }
}
