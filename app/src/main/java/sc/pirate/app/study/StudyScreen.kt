package sc.pirate.app.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import sc.pirate.app.api.model.SongStudyAttemptResult
import sc.pirate.app.api.model.SongStudyExercise
import sc.pirate.app.api.model.SongStudyPayload
import sc.pirate.app.theme.PirateTokens
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
    val sounds = rememberStudyFeedbackSounds()

    // Fires on the server's verdict, once per result — never optimistically, so the viewer never
    // hears "correct" for something the server goes on to reject.
    LaunchedEffect(state.lastResult) {
        val outcome = state.lastResult?.outcome ?: return@LaunchedEffect
        sounds.play(correct = outcome == "correct")
    }

    LaunchedEffect(communityId, postId, hasSession) {
        viewModel.load(communityId, postId, hasSession)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.pack?.title ?: "Study",
                            color = PirateTokens.colors.textPrimary,
                        )
                        state.pack?.artistName?.takeIf { it.isNotBlank() }?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.labelMedium,
                                color = PirateTokens.colors.textSecondary,
                            )
                        }
                    }
                },
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
        bottomBar = {
            val pack = state.pack
            val exercise = state.currentExercise
            if (pack?.access == "ready" && !state.completed && exercise != null) {
                Surface(
                    color = PirateTokens.colors.bgPage,
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        ExerciseActions(
                            exercise = exercise,
                            state = state,
                            onRetry = viewModel::retry,
                            onNext = viewModel::next,
                            onRecordingReady = viewModel::transcribeRecording,
                        )
                    }
                }
            }
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
                    onSelectOption = viewModel::selectOption,
                )
            }
        }
    }
}

@Composable
private fun StudyContent(
    pack: SongStudyPayload,
    state: StudyUiState,
    onSelectOption: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (pack.access) {
            "ready" -> when {
                state.completed -> CompleteSurface(pack = pack, correctCount = state.correctCount)
                else -> {
                    val exercise = state.currentExercise
                    if (exercise == null) {
                        CompleteSurface(pack = pack, correctCount = state.correctCount)
                    } else {
                        ExerciseHeader(index = state.index, total = state.queue.size)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            when (exercise.type) {
                                "translation_choice" -> TranslationChoiceCard(
                                    exercise = exercise,
                                    state = state,
                                    onSelectOption = onSelectOption,
                                )
                                "say_it_back" -> SayItBackCard(
                                    exercise = exercise,
                                    state = state,
                                )
                                else -> StatusCard(
                                    title = "Unsupported exercise",
                                    description = "This exercise type isn't supported in this app version.",
                                    tone = StatusTone.Default,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        state.attemptError?.let { FormNote(message = it, tone = FormTone.Error) }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { (index + 1).toFloat() / total.coerceAtLeast(1) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = PirateTokens.colors.accentBrand,
            trackColor = PirateTokens.colors.borderSoft,
        )
        Text(
            text = "${index + 1} of $total",
            style = MaterialTheme.typography.labelLarge,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

@Composable
private fun TranslationChoiceCard(
    exercise: SongStudyExercise,
    state: StudyUiState,
    onSelectOption: (String) -> Unit,
) {
    val result = state.lastResult
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        exercise.question?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.textSecondary)
        }
        Text(
            text = exercise.promptText,
            style = MaterialTheme.typography.headlineMedium,
            color = PirateTokens.colors.textPrimary,
        )
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
                    .clickable(enabled = result == null && !state.submitting) {
                        onSelectOption(option.id)
                    },
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
) {
    val result = state.lastResult
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(PirateTokens.radius.xl),
            color = PirateTokens.colors.surfaceSubtle,
            border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Say it back",
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textSecondary,
                )
                Text(
                    text = exercise.promptText,
                    style = MaterialTheme.typography.headlineLarge,
                    color = PirateTokens.colors.textPrimary,
                )
            }
        }
        if (result != null) {
            val correct = result.outcome == "correct"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PirateTokens.radius.lg),
                color = if (correct) {
                    PirateTokens.colors.accentSuccess.copy(alpha = 0.10f)
                } else {
                    PirateTokens.colors.accentDanger.copy(alpha = 0.10f)
                },
                border = BorderStroke(
                    1.dp,
                    if (correct) PirateTokens.colors.accentSuccess else PirateTokens.colors.accentDanger,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (correct) "Correct." else "Incorrect.",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (correct) PirateTokens.colors.accentSuccess else PirateTokens.colors.accentDanger,
                    )
                    state.sayItBackInput.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = "You said, “$it”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PirateTokens.colors.textSecondary,
                        )
                    }
                }
            }
        }
        if (result != null && result.outcome != "correct") {
            exercise.referenceText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = PirateTokens.colors.textPrimary,
                )
            }
        }
        if (result != null) {
            result.feedback?.let { feedback ->
                if (feedback.missing.isNotEmpty()) {
                    Text(
                        text = "Missing: ${feedback.missing.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
                if (feedback.extra.isNotEmpty()) {
                    Text(
                        text = "Extra: ${feedback.extra.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
            }
            result.nextReviewHint?.let { hint ->
                Text(
                    text = reviewHintLabel(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        } else {
            state.transcriptionError?.let { FormNote(message = it, tone = FormTone.Error) }
        }
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
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onRecordingReady: (java.io.File) -> Unit,
) {
    val result = state.lastResult
    when (exercise.type) {
        "say_it_back" -> when {
            result != null -> PirateButton(
                text = "Continue",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> key(exercise.id, state.attemptNumber) {
                StudyAudioRecorder(
                    enabled = !state.submitting,
                    checking = state.transcribing || state.submitting,
                    onRecordingReady = onRecordingReady,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        "translation_choice" -> when {
            result == null -> Unit
            result.outcome == "incorrect" && result.attemptsRemaining > 0 -> {
                PirateButton(
                    text = "Try again",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> PirateButton(
                text = "Continue",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        else -> {
            PirateButton(
                text = "Continue",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
