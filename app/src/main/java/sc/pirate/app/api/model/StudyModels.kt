package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Derived from services/contracts/src/index.ts — the shared @pirate/api-contracts package that
 * web consumes — rather than from web's hand-written client, so these do not inherit whatever
 * subset the web UI happens to render today.
 */

/**
 * Whether a post offers studying, as reported on the post itself. This is what a feed rail reads
 * to decide whether to draw a Study action at all: only `ready` is actionable.
 */
@Serializable
data class SongStudyCapability(
    val status: String,
    @SerialName("exercise_count") val exerciseCount: Int? = null,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String? = null,
    val reasons: List<SongFeatureCapabilityReason> = emptyList(),
) {
    val ready: Boolean get() = status == STATUS_READY

    companion object {
        const val STATUS_READY = "ready"
        const val STATUS_LOCKED = "locked"
        const val STATUS_PROCESSING = "processing"
        const val STATUS_UNAVAILABLE = "unavailable"
    }
}

@Serializable
data class SongFeatureCapabilityReason(
    val code: String,
    val kind: String,
    @SerialName("owner_action") val ownerAction: String? = null,
)

@Serializable
data class SongStudySessionSummary(
    val id: String? = null,
    val status: String,
    @SerialName("due_count") val dueCount: Int = 0,
    @SerialName("served_count") val servedCount: Int = 0,
    @SerialName("total_units") val totalUnits: Int = 0,
    @SerialName("required_correct_count") val requiredCorrectCount: Int = 0,
    @SerialName("max_presentations") val maxPresentations: Int = 0,
    @SerialName("presentation_count") val presentationCount: Int = 0,
    @SerialName("completed_exercise_count") val completedExerciseCount: Int = 0,
    @SerialName("first_pass_correct_count") val firstPassCorrectCount: Int = 0,
    @SerialName("mastered_exercise_count") val masteredExerciseCount: Int = 0,
    val qualified: Boolean = false,
    /** Unix seconds. Present when the session is caught up and reviews resume later. */
    @SerialName("next_due_at") val nextDueAt: Long? = null,
) {
    /**
     * Web treats an absent session id as "caught up", not as an error: study-route.tsx gates on
     * `exercises.length === 0 || !study.session?.id` and renders a blocked surface. Attempts are
     * only ever submitted when an id exists, because submitting against a stale one is the
     * "session expired, reopen the lesson" path.
     */
    val submittable: Boolean get() = !id.isNullOrBlank()
}

/**
 * One exercise.
 *
 * The contract models this as a discriminated union on `type`, but it is flattened here with the
 * variant-specific fields nullable, deliberately: a sealed polymorphic decode throws on an
 * unrecognised discriminator, so a third exercise type added server-side would break the whole
 * payload rather than one card. [SAY_IT_BACK] and [TRANSLATION_CHOICE] are the two that exist
 * today; anything else deserializes and can be skipped by the UI.
 */
@Serializable
data class SongStudyExercise(
    val id: String,
    val type: String,
    @SerialName("line_id") val lineId: String,
    @SerialName("line_index") val lineIndex: Int,
    @SerialName("prompt_text") val promptText: String,
    @SerialName("max_attempts") val maxAttempts: Int,
    @SerialName("presentation_count") val presentationCount: Int = 0,
    val mastered: Boolean = false,
    @SerialName("first_outcome") val firstOutcome: String? = null,
    // say_it_back
    @SerialName("reference_text") val referenceText: String? = null,
    @SerialName("translation_text") val translationText: String? = null,
    // translation_choice
    val question: String? = null,
    val options: List<SongStudyExerciseOption> = emptyList(),
) {
    val known: Boolean get() = type == SAY_IT_BACK || type == TRANSLATION_CHOICE

    companion object {
        const val SAY_IT_BACK = "say_it_back"
        const val TRANSLATION_CHOICE = "translation_choice"
    }
}

@Serializable
data class SongStudyExerciseOption(
    val id: String,
    val text: String,
)

@Serializable
data class SongStudyPayload(
    @SerialName("post_id") val postId: String,
    @SerialName("community_id") val communityId: String,
    val access: String,
    val title: String,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("artwork_src") val artworkSrc: String? = null,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String? = null,
    @SerialName("exercise_count") val exerciseCount: Int = 0,
    val exercises: List<SongStudyExercise> = emptyList(),
    val session: SongStudySessionSummary? = null,
    @SerialName("study_pack_version") val studyPackVersion: Int? = null,
    @SerialName("generated_at") val generatedAt: Long? = null,
    @SerialName("locked_reason") val lockedReason: String? = null,
    @SerialName("unavailable_reason") val unavailableReason: String? = null,
) {
    val ready: Boolean get() = access == ACCESS_READY

    companion object {
        const val ACCESS_READY = "ready"
        const val ACCESS_LOCKED = "locked"
        const val ACCESS_PROCESSING = "processing"
        const val ACCESS_UNAVAILABLE = "unavailable"
    }
}

@Serializable
data class SongStudyAttemptRequest(
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("exercise_id") val exerciseId: String,
    val type: String,
    @SerialName("attempt_number") val attemptNumber: Int,
    @SerialName("selected_option_id") val selectedOptionId: String? = null,
    val transcript: String? = null,
)

@Serializable
data class SongStudyAttemptFeedback(
    val matched: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val extra: List<String> = emptyList(),
)

@Serializable
data class SongStudyProgress(
    @SerialName("study_attempt_count") val studyAttemptCount: Int = 0,
    @SerialName("study_correct_count") val studyCorrectCount: Int = 0,
    @SerialName("study_target_count") val studyTargetCount: Int = 0,
    @SerialName("qualified_today") val qualifiedToday: Boolean = false,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("next_due_at") val nextDueAt: Long? = null,
)

@Serializable
data class SongStudyAttemptResult(
    @SerialName("exercise_id") val exerciseId: String,
    val outcome: String,
    @SerialName("attempts_remaining") val attemptsRemaining: Int = 0,
    @SerialName("correct_option_id") val correctOptionId: String? = null,
    val feedback: SongStudyAttemptFeedback? = null,
    @SerialName("next_review_hint") val nextReviewHint: String? = null,
    val session: SongStudySessionSummary? = null,
    @SerialName("study_progress") val studyProgress: SongStudyProgress? = null,
) {
    val correct: Boolean get() = outcome == OUTCOME_CORRECT

    companion object {
        const val OUTCOME_CORRECT = "correct"
        const val OUTCOME_INCORRECT = "incorrect"
        const val OUTCOME_REVEALED = "revealed"
    }
}

@Serializable
data class SongStudyTranscriptionResponse(
    val provider: String? = null,
    val model: String? = null,
    val text: String,
    val confidence: Double? = null,
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("language_probability") val languageProbability: Double? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
)

@Serializable
data class SongStreakLeaderboardIdentity(
    @SerialName("user_id") val userId: String,
    val handle: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
)

@Serializable
data class SongStreakLeaderboardEntry(
    val rank: Int,
    val identity: SongStreakLeaderboardIdentity,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("best_streak") val bestStreak: Int = 0,
    @SerialName("total_qualified_days") val totalQualifiedDays: Int = 0,
    @SerialName("streak_started_date") val streakStartedDate: String? = null,
    @SerialName("last_qualified_date") val lastQualifiedDate: String? = null,
    @SerialName("is_viewer") val isViewer: Boolean = false,
)

@Serializable
data class SongStreakLeaderboard(
    @SerialName("post_id") val postId: String? = null,
    @SerialName("community_id") val communityId: String? = null,
    val date: String? = null,
    val entries: List<SongStreakLeaderboardEntry> = emptyList(),
)
