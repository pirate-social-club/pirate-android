package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Song Study API models. Mirrors core/specs/api/src/components/schemas/song-study.yaml.
 *
 * Access is server-authoritative: the client MUST branch on [SongStudyPayload.access]
 * and never re-derive study access from the post payload shape. Enum-like fields are
 * typed as String so an unknown future value never fails decode (matches the tolerant
 * style used elsewhere in ApiModels, e.g. Post.accessMode).
 */

/**
 * Viewer-specific display hint on a localized post read (LocalizedPostResponse.study_capability).
 * `status` may be used to show the post-card Study CTA, but the client MUST load the study pack
 * (GET .../study) before rendering any study content.
 */
@Serializable
data class SongStudyCapability(
    /** ready | locked | processing | unavailable */
    val status: String,
    @SerialName("exercise_count") val exerciseCount: Int? = null,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String? = null,
)

/** Canonical study pack for one song post. */
@Serializable
data class SongStudyPayload(
    @SerialName("object") val contractObject: String? = null,
    @SerialName("post_id") val postId: String,
    @SerialName("community_id") val communityId: String,
    /** ready | locked | processing | unavailable — SOLE access authority. */
    val access: String,
    val title: String,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("artwork_src") val artworkSrc: String? = null,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("target_language") val targetLanguage: String? = null,
    @SerialName("exercise_count") val exerciseCount: Int = 0,
    val exercises: List<SongStudyExercise> = emptyList(),
    @SerialName("study_pack_version") val studyPackVersion: Int? = null,
    @SerialName("generated_at") val generatedAt: Long? = null,
    /** purchase_required | membership_required | age_required (only when access == locked). */
    @SerialName("locked_reason") val lockedReason: String? = null,
    /** not_song | no_lyrics | unsupported_language | generation_failed (only when access == unavailable). */
    @SerialName("unavailable_reason") val unavailableReason: String? = null,
)

/**
 * One study exercise. Hand-discriminated on [type] (say_it_back | translation_choice) — the
 * codebase decodes type unions as a flat data class + `when (type)` (see Post.postType) rather
 * than kotlinx polymorphism, and this is robust to unknown future types. No variant carries the
 * correct answer; per-variant fields are nullable and populated only for the matching [type].
 */
@Serializable
data class SongStudyExercise(
    val id: String,
    /** say_it_back | translation_choice */
    val type: String,
    @SerialName("line_id") val lineId: String,
    @SerialName("line_index") val lineIndex: Int,
    @SerialName("prompt_text") val promptText: String,
    @SerialName("max_attempts") val maxAttempts: Int,
    // say_it_back only:
    /** Visible target line the learner is asked to produce. NOT a grading secret. */
    @SerialName("reference_text") val referenceText: String? = null,
    @SerialName("translation_text") val translationText: String? = null,
    // translation_choice only:
    val question: String? = null,
    /** Server-shuffled; correct option NOT identified. Render in array order, never re-sort. */
    val options: List<SongStudyOption> = emptyList(),
)

@Serializable
data class SongStudyOption(
    val id: String,
    val text: String,
)

/**
 * One attempt at one exercise. Exactly one of [selectedOptionId] (translation_choice) or
 * [transcript] (say_it_back) is present, matching [type]. Writes are idempotent per
 * [idempotencyKey].
 */
@Serializable
data class SongStudyAttemptRequest(
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("exercise_id") val exerciseId: String,
    /** say_it_back | translation_choice */
    val type: String,
    /** 1-based; server rejects values > max_attempts. */
    @SerialName("attempt_number") val attemptNumber: Int,
    @SerialName("selected_option_id") val selectedOptionId: String? = null,
    val transcript: String? = null,
)

/** Server verdict for one submitted attempt. */
@Serializable
data class SongStudyAttemptResult(
    @SerialName("object") val contractObject: String? = null,
    @SerialName("exercise_id") val exerciseId: String,
    /** correct | incorrect | revealed */
    val outcome: String,
    @SerialName("attempts_remaining") val attemptsRemaining: Int,
    /** Disclosed only once the attempt is spent (outcome correct|revealed). */
    @SerialName("correct_option_id") val correctOptionId: String? = null,
    val feedback: SongStudyAttemptFeedback? = null,
    /** again | hard | good | easy — human-facing hint from the server FSRS update. */
    @SerialName("next_review_hint") val nextReviewHint: String? = null,
)

@Serializable
data class SongStudyAttemptFeedback(
    val matched: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val extra: List<String> = emptyList(),
)

/** Final transcript for one say-it-back recording. Not itself a grade. */
@Serializable
data class SongStudyTranscriptionResponse(
    @SerialName("object") val contractObject: String? = null,
    /** elevenlabs */
    val provider: String,
    val model: String,
    val text: String,
    val confidence: Double? = null,
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("language_probability") val languageProbability: Double? = null,
    @SerialName("duration_seconds") val durationSeconds: Double? = null,
)
