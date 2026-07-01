package sc.pirate.app.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.SongStudyAttemptRequest
import sc.pirate.app.api.model.SongStudyAttemptResult
import sc.pirate.app.api.model.SongStudyExercise
import sc.pirate.app.api.model.SongStudyPayload
import java.util.UUID

/**
 * Drives the Song Study screen. Access is server-authoritative: the UI branches on
 * [SongStudyPayload.access] and the per-attempt verdict from [SongStudyAttemptResult];
 * it never decides correctness or access locally.
 *
 * v1 scope: renders `ready` packs (translation_choice + say_it_back), submits attempts, and
 * shows verdicts. `say_it_back` uses a typed transcript (the mic/ElevenLabs capture path is a
 * documented follow-up). Locked purchase UX is a follow-up (Android has no asset-purchase flow
 * wired yet), so the locked state is informational.
 */
data class StudyUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val pack: SongStudyPayload? = null,
    val started: Boolean = false,
    val index: Int = 0,
    val attemptNumber: Int = 1,
    val selectedOptionId: String? = null,
    val sayItBackInput: String = "",
    val submitting: Boolean = false,
    val attemptError: String? = null,
    val lastResult: SongStudyAttemptResult? = null,
    val correctCount: Int = 0,
    val completed: Boolean = false,
) {
    val currentExercise: SongStudyExercise?
        get() = pack?.exercises?.getOrNull(index)
}

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val api get() = app.apiClient

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()

    private var communityId: String = ""
    private var postId: String = ""

    fun load(communityId: String, postId: String, hasSession: Boolean) {
        this.communityId = communityId
        this.postId = postId
        currentAttemptKey = null
        currentAttemptKeyFor = null
        viewModelScope.launch {
            _state.value = StudyUiState(loading = true)
            if (!hasSession) {
                // Study requires auth (server returns 401 for logged-out callers).
                _state.value = StudyUiState(loading = false, error = "Sign in to study this song.")
                return@launch
            }
            try {
                val pack = api.communities.getStudyPack(communityId, postId)
                _state.value = StudyUiState(loading = false, pack = pack)
            } catch (e: Exception) {
                _state.value = StudyUiState(loading = false, error = e.message ?: "Could not load study.")
            }
        }
    }

    fun start() {
        // Every new run starts with no cached attempt key (lifecycle invariant).
        currentAttemptKey = null
        currentAttemptKeyFor = null
        _state.value = _state.value.copy(
            started = true,
            index = 0,
            attemptNumber = 1,
            selectedOptionId = null,
            sayItBackInput = "",
            lastResult = null,
            attemptError = null,
            correctCount = 0,
            completed = false,
        )
    }

    fun selectOption(optionId: String) {
        // Ignore selection changes once the current attempt is spent.
        if (_state.value.lastResult != null) return
        _state.value = _state.value.copy(selectedOptionId = optionId, attemptError = null)
    }

    fun updateSayItBack(text: String) {
        if (_state.value.lastResult != null) return
        _state.value = _state.value.copy(sayItBackInput = text, attemptError = null)
    }

    fun submit() {
        val current = _state.value
        val exercise = current.currentExercise ?: return
        if (current.submitting || current.lastResult != null) return

        // One STABLE idempotency key per (exercise, attemptNumber): a lost-response retry
        // must resend the SAME key so the server replays the original result rather than
        // rejecting a duplicate exercise_id+attempt_number under a fresh key (409).
        val idempKey = stableAttemptKey(exercise.id, current.attemptNumber)
        val request = when (exercise.type) {
            "translation_choice" -> {
                val optionId = current.selectedOptionId ?: return
                SongStudyAttemptRequest(
                    idempotencyKey = idempKey,
                    exerciseId = exercise.id,
                    type = exercise.type,
                    attemptNumber = current.attemptNumber,
                    selectedOptionId = optionId,
                )
            }
            "say_it_back" -> {
                val transcript = current.sayItBackInput.trim()
                if (transcript.isEmpty()) return
                SongStudyAttemptRequest(
                    idempotencyKey = idempKey,
                    exerciseId = exercise.id,
                    type = exercise.type,
                    attemptNumber = current.attemptNumber,
                    transcript = transcript,
                )
            }
            else -> return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, attemptError = null)
            try {
                val result = api.communities.submitStudyAttempt(communityId, postId, request)
                _state.value = _state.value.copy(
                    submitting = false,
                    lastResult = result,
                    correctCount = _state.value.correctCount + if (result.outcome == "correct") 1 else 0,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    submitting = false,
                    attemptError = e.message ?: "Could not submit attempt.",
                )
            }
        }
    }

    /** Retry the same exercise after an incorrect, non-final attempt. */
    fun retry() {
        val result = _state.value.lastResult ?: return
        if (result.outcome != "incorrect" || result.attemptsRemaining <= 0) return
        _state.value = _state.value.copy(
            attemptNumber = _state.value.attemptNumber + 1,
            selectedOptionId = null,
            sayItBackInput = "",
            lastResult = null,
            attemptError = null,
        )
    }

    fun next() {
        val pack = _state.value.pack ?: return
        val nextIndex = _state.value.index + 1
        if (nextIndex >= pack.exercises.size) {
            _state.value = _state.value.copy(completed = true, lastResult = null)
            return
        }
        _state.value = _state.value.copy(
            index = nextIndex,
            attemptNumber = 1,
            selectedOptionId = null,
            sayItBackInput = "",
            lastResult = null,
            attemptError = null,
        )
    }

    // The idempotency key currently in force, and the "exerciseId:attemptNumber" it was minted
    // for. Reused across repeated submit() calls of the same attempt; rotated only when the
    // exercise or attemptNumber changes (advance/retry).
    private var currentAttemptKey: String? = null
    private var currentAttemptKeyFor: String? = null

    private fun stableAttemptKey(exerciseId: String, attemptNumber: Int): String {
        val keyFor = "$exerciseId:$attemptNumber"
        val existing = currentAttemptKey
        if (existing != null && currentAttemptKeyFor == keyFor) return existing
        val minted = "study:$exerciseId:$attemptNumber:${UUID.randomUUID()}"
        currentAttemptKey = minted
        currentAttemptKeyFor = keyFor
        return minted
    }
}
