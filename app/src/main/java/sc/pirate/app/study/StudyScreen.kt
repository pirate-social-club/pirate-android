package sc.pirate.app.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import sc.pirate.app.api.model.SongStudyAttemptResult
import sc.pirate.app.api.model.SongStudyExercise
import sc.pirate.app.api.model.SongStudyPayload
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    communityId: String,
    postId: String,
    hasSession: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StudyViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(communityId, postId, hasSession) {
        viewModel.load(communityId, postId, hasSession)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Study", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhosphorIcons.X,
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
            val pack = state.pack
            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                    }
                }
                state.error != null -> {
                    StatusCard(
                        title = "Study unavailable",
                        description = state.error.orEmpty(),
                        tone = StatusTone.Warning,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                pack == null -> {
                    StatusCard(
                        title = "Study unavailable",
                        description = "No study pack was returned.",
                        tone = StatusTone.Warning,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                else -> StudyContent(
                    pack = pack,
                    state = state,
                    onStart = viewModel::start,
                    onSelectOption = viewModel::selectOption,
                    onSayItBackChange = viewModel::updateSayItBack,
                    onRecordingReady = viewModel::transcribeRecording,
                    onSubmit = viewModel::submit,
                    onRetry = viewModel::retry,
                    onNext = viewModel::next,
                )
            }
        }
    }
}

@Composable
private fun StudyContent(
    pack: SongStudyPayload,
    state: StudyUiState,
    onStart: () -> Unit,
    onSelectOption: (String) -> Unit,
    onSayItBackChange: (String) -> Unit,
    onRecordingReady: (java.io.File) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (pack.access) {
            "ready" -> when {
                !state.started -> StartSurface(pack = pack, onStart = onStart)
                state.completed -> CompleteSurface(pack = pack, correctCount = state.correctCount)
                else -> {
                    val exercise = state.currentExercise
                    if (exercise == null) {
                        CompleteSurface(pack = pack, correctCount = state.correctCount)
                    } else {
                        ExerciseHeader(index = state.index, total = pack.exercises.size)
                        when (exercise.type) {
                            "translation_choice" -> TranslationChoiceCard(
                                exercise = exercise,
                                state = state,
                                onSelectOption = onSelectOption,
                            )
                            "say_it_back" -> SayItBackCard(
                                exercise = exercise,
                                state = state,
                                onSayItBackChange = onSayItBackChange,
                                onRecordingReady = onRecordingReady,
                            )
                            else -> StatusCard(
                                title = "Unsupported exercise",
                                description = "This exercise type isn't supported in this app version.",
                                tone = StatusTone.Default,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        state.attemptError?.let { FormNote(message = it, tone = FormTone.Error) }
                        ExerciseActions(
                            exercise = exercise,
                            state = state,
                            onSubmit = onSubmit,
                            onRetry = onRetry,
                            onNext = onNext,
                        )
                    }
                }
            }
            "locked" -> StatusCard(
                title = "Study is locked",
                description = lockedMessage(pack.lockedReason),
                tone = StatusTone.Warning,
                modifier = Modifier.fillMaxWidth(),
            )
            "processing" -> StatusCard(
                title = "Study is being prepared",
                description = "Exercises for this song are still generating. Check back shortly.",
                tone = StatusTone.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            "unavailable" -> StatusCard(
                title = "Study isn't available",
                description = unavailableMessage(pack.unavailableReason),
                tone = StatusTone.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> StatusCard(
                title = "Study unavailable",
                description = "This song can't be studied right now.",
                tone = StatusTone.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StartSurface(pack: SongStudyPayload, onStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = pack.title,
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )
        pack.artistName?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.textSecondary)
        }
        languagePairLabel(pack)?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.textSecondary)
        }
        Text(
            text = "${pack.exerciseCount} ${if (pack.exerciseCount == 1) "exercise" else "exercises"}",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        PirateButton(
            text = "Start studying",
            onClick = onStart,
            enabled = pack.exercises.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CompleteSurface(pack: SongStudyPayload, correctCount: Int) {
    val total = pack.exercises.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Session complete",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )
        Text(
            text = "$correctCount / $total correct",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

@Composable
private fun ExerciseHeader(index: Int, total: Int) {
    Text(
        text = "Exercise ${index + 1} of $total",
        style = MaterialTheme.typography.labelLarge,
        color = PirateTokens.colors.textSecondary,
    )
}

@Composable
private fun TranslationChoiceCard(
    exercise: SongStudyExercise,
    state: StudyUiState,
    onSelectOption: (String) -> Unit,
) {
    val result = state.lastResult
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = exercise.promptText,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        exercise.question?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.textSecondary)
        }
        exercise.options.forEach { option ->
            val isSelected = state.selectedOptionId == option.id
            val isCorrect = result?.correctOptionId == option.id
            val borderColor = when {
                isCorrect -> PirateTokens.colors.accentSuccess
                isSelected && result != null -> PirateTokens.colors.accentDanger
                isSelected -> PirateTokens.colors.accentBrand
                else -> PirateTokens.colors.borderSoft
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = result == null) { onSelectOption(option.id) },
                shape = RoundedCornerShape(PirateTokens.radius.lg),
                color = PirateTokens.colors.surfaceSubtle,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Text(
                    text = option.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PirateTokens.colors.textPrimary,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        VerdictNote(result)
    }
}

@Composable
private fun SayItBackCard(
    exercise: SongStudyExercise,
    state: StudyUiState,
    onSayItBackChange: (String) -> Unit,
    onRecordingReady: (java.io.File) -> Unit,
) {
    val result = state.lastResult
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = exercise.promptText,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        exercise.referenceText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = PirateTokens.colors.textPrimary,
            )
        }
        exercise.translationText?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.textSecondary)
        }
        StudyAudioRecorder(
            enabled = result == null,
            transcribing = state.transcribing,
            onRecordingReady = onRecordingReady,
            modifier = Modifier.fillMaxWidth(),
        )
        state.transcriptionError?.let { FormNote(message = it, tone = FormTone.Error) }
        OutlinedTextField(
            value = state.sayItBackInput,
            onValueChange = onSayItBackChange,
            enabled = result == null,
            label = { Text("Transcript (you can edit this)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        result?.feedback?.let { feedback ->
            if (feedback.missing.isNotEmpty()) {
                Text(
                    text = "Missing: ${feedback.missing.joinToString(" ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.accentWarning,
                )
            }
            if (feedback.extra.isNotEmpty()) {
                Text(
                    text = "Extra: ${feedback.extra.joinToString(" ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        }
        VerdictNote(result)
    }
}

@Composable
private fun VerdictNote(result: SongStudyAttemptResult?) {
    result ?: return
    val (message, tone) = when (result.outcome) {
        "correct" -> "Correct" to FormTone.Warning
        "revealed" -> "Answer revealed" to FormTone.Error
        else -> "Not quite — try again" to FormTone.Error
    }
    // Correct uses the success accent via a plain Text; FormNote only has Warning/Error.
    if (result.outcome == "correct") {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.accentSuccess)
    } else {
        FormNote(message = message, tone = tone)
    }
    result.nextReviewHint?.let { hint ->
        Text(
            text = reviewHintLabel(hint),
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

@Composable
private fun ExerciseActions(
    exercise: SongStudyExercise,
    state: StudyUiState,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onNext: () -> Unit,
) {
    val result = state.lastResult
    when {
        result == null -> {
            val canSubmit = when (exercise.type) {
                "translation_choice" -> state.selectedOptionId != null
                "say_it_back" -> state.sayItBackInput.isNotBlank()
                else -> false
            }
            PirateButton(
                text = "Check",
                onClick = onSubmit,
                enabled = canSubmit && !state.submitting,
                loading = state.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        result.outcome == "incorrect" && result.attemptsRemaining > 0 -> {
            PirateButton(
                text = "Try again (${result.attemptsRemaining} left)",
                onClick = onRetry,
                variant = ButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> {
            PirateButton(
                text = "Next",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun languagePairLabel(pack: SongStudyPayload): String? {
    val source = pack.sourceLanguage
    val target = pack.targetLanguage
    return when {
        source != null && target != null -> "$source → $target"
        source != null -> source
        target != null -> target
        else -> null
    }
}

private fun lockedMessage(reason: String?): String = when (reason) {
    "purchase_required" -> "Buy this song to unlock study. Purchasing is coming to the app soon."
    "membership_required" -> "Join this community to unlock study for this song."
    "age_required" -> "Age verification is required to study this song."
    else -> "You don't have access to study this song yet."
}

private fun unavailableMessage(reason: String?): String = when (reason) {
    "not_song" -> "Study is only available on song posts."
    "no_lyrics" -> "This song has no lyrics to study."
    "unsupported_language" -> "Study isn't supported for this song's language yet."
    "generation_failed" -> "Study couldn't be prepared for this song."
    else -> "This song can't be studied right now."
}

private fun reviewHintLabel(hint: String): String = when (hint) {
    "again" -> "You'll see this again soon."
    "hard" -> "Scheduled to review after a short break."
    "good" -> "Scheduled for later review."
    "easy" -> "You've got this — scheduled far out."
    else -> ""
}
