package sc.pirate.app.api.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are shaped from services/contracts/src/index.ts, the shared package web consumes.
 * These exist to catch the failure that would otherwise only show up on a device against staging:
 * a field renamed or re-nested server-side, decoding into a default and silently producing an
 * empty study screen.
 */
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

class StudyPayloadDecodingTest {

    private val payloadJson = """
    {
      "object": "song_study_payload",
      "post_id": "post_pst_1",
      "community_id": "cmt_1",
      "access": "ready",
      "title": "Arkansas Blues",
      "artist_name": "A Singer",
      "artwork_src": "https://media.test/art.png",
      "source_language": "en",
      "target_language": "es",
      "exercise_count": 2,
      "exercises": [
        {
          "id": "ex_1", "type": "say_it_back", "line_id": "l1", "line_index": 0,
          "prompt_text": "Say this line", "reference_text": "the reference",
          "translation_text": "la referencia", "max_attempts": 3,
          "presentation_count": 1, "mastered": false, "first_outcome": null
        },
        {
          "id": "ex_2", "type": "translation_choice", "line_id": "l2", "line_index": 1,
          "prompt_text": "Pick one", "question": "What does it mean?",
          "options": [{"id": "o1", "text": "first"}, {"id": "o2", "text": "second"}],
          "max_attempts": 2, "presentation_count": 0, "mastered": false, "first_outcome": null
        }
      ],
      "session": {
        "id": "ses_1", "status": "active", "due_count": 4, "served_count": 2,
        "total_units": 10, "required_correct_count": 8, "max_presentations": 3,
        "presentation_count": 2, "completed_exercise_count": 1,
        "first_pass_correct_count": 1, "mastered_exercise_count": 0, "qualified": false
      },
      "study_pack_version": 3,
      "generated_at": 1753000000
    }
    """.trimIndent()

    @Test
    fun `decodes a ready payload with both exercise types`() {
        val payload = json.decodeFromString(SongStudyPayload.serializer(), payloadJson)

        assertTrue(payload.ready)
        assertEquals("Arkansas Blues", payload.title)
        assertEquals(2, payload.exercises.size)
        assertEquals("es", payload.targetLanguage)
    }

    @Test
    fun `say it back keeps its reference text and has no options`() {
        val exercise = json.decodeFromString(SongStudyPayload.serializer(), payloadJson).exercises[0]

        assertEquals(SongStudyExercise.SAY_IT_BACK, exercise.type)
        assertEquals("the reference", exercise.referenceText)
        assertTrue(exercise.options.isEmpty())
        assertTrue(exercise.known)
    }

    @Test
    fun `translation choice keeps its options and has no reference text`() {
        val exercise = json.decodeFromString(SongStudyPayload.serializer(), payloadJson).exercises[1]

        assertEquals(SongStudyExercise.TRANSLATION_CHOICE, exercise.type)
        assertEquals(listOf("first", "second"), exercise.options.map { it.text })
        assertNull(exercise.referenceText)
    }

    @Test
    fun `session summary survives the round trip`() {
        val session = json.decodeFromString(SongStudyPayload.serializer(), payloadJson).session

        assertEquals("active", session?.status)
        assertEquals(4, session?.dueCount)
        assertEquals(8, session?.requiredCorrectCount)
        assertFalse(session?.qualified ?: true)
    }

    /**
     * The contract flattens a union on `type`. An unrecognised variant must decode rather than
     * throw, or one new server-side exercise type takes down the entire study screen instead of a
     * single card.
     */
    @Test
    fun `an unknown exercise type decodes and is flagged unknown`() {
        val payload = json.decodeFromString(
            SongStudyPayload.serializer(),
            """
            {"post_id":"p","community_id":"c","access":"ready","title":"t","exercise_count":1,
             "exercises":[{"id":"ex","type":"fill_in_the_blank","line_id":"l","line_index":0,
             "prompt_text":"x","max_attempts":1}]}
            """.trimIndent(),
        )

        assertEquals(1, payload.exercises.size)
        assertFalse(payload.exercises[0].known)
    }

    @Test
    fun `a locked payload carries its reason and is not ready`() {
        val payload = json.decodeFromString(
            SongStudyPayload.serializer(),
            """
            {"post_id":"p","community_id":"c","access":"locked","title":"t","exercise_count":0,
             "locked_reason":"membership_required"}
            """.trimIndent(),
        )

        assertFalse(payload.ready)
        assertEquals("membership_required", payload.lockedReason)
        assertTrue(payload.exercises.isEmpty())
    }

    @Test
    fun `an unavailable payload carries its reason`() {
        val payload = json.decodeFromString(
            SongStudyPayload.serializer(),
            """
            {"post_id":"p","community_id":"c","access":"unavailable","title":"t",
             "exercise_count":0,"unavailable_reason":"no_lyrics"}
            """.trimIndent(),
        )

        assertEquals("no_lyrics", payload.unavailableReason)
        assertFalse(payload.ready)
    }
}

class StudyAttemptDecodingTest {

    @Test
    fun `decodes a correct attempt with progress`() {
        val result = json.decodeFromString(
            SongStudyAttemptResult.serializer(),
            """
            {"object":"song_study_attempt_result","exercise_id":"ex_1","outcome":"correct",
             "attempts_remaining":2,"feedback":{"matched":["a","b"],"missing":[],"extra":["c"]},
             "next_review_hint":"good",
             "study_progress":{"study_attempt_count":5,"study_correct_count":4,
              "study_target_count":8,"qualified_today":false,"current_streak":3}}
            """.trimIndent(),
        )

        assertTrue(result.correct)
        assertEquals(2, result.attemptsRemaining)
        assertEquals(listOf("a", "b"), result.feedback?.matched)
        assertEquals(listOf("c"), result.feedback?.extra)
        assertEquals(3, result.studyProgress?.currentStreak)
    }

    @Test
    fun `decodes an incorrect choice attempt carrying the correct option`() {
        val result = json.decodeFromString(
            SongStudyAttemptResult.serializer(),
            """
            {"exercise_id":"ex_2","outcome":"incorrect","attempts_remaining":1,
             "correct_option_id":"o2"}
            """.trimIndent(),
        )

        assertFalse(result.correct)
        assertEquals("o2", result.correctOptionId)
        assertNull(result.feedback)
    }

    @Test
    fun `an attempt request omits nulls so the server sees only what applies`() {
        val encoded = json.encodeToString(
            SongStudyAttemptRequest.serializer(),
            SongStudyAttemptRequest(
                idempotencyKey = "idem_1",
                sessionId = "ses_1",
                exerciseId = "ex_1",
                type = SongStudyExercise.SAY_IT_BACK,
                attemptNumber = 1,
                transcript = "what I said",
            ),
        )

        assertTrue(encoded.contains("\"transcript\":\"what I said\""))
        assertFalse(encoded.contains("selected_option_id"))
        assertTrue(encoded.contains("\"idempotency_key\":\"idem_1\""))
    }
}

class StudyCapabilityAndStreakDecodingTest {

    @Test
    fun `only a ready capability is actionable`() {
        val ready = json.decodeFromString(
            SongStudyCapability.serializer(),
            """{"status":"ready","exercise_count":12,"source_language":"en"}""",
        )
        val locked = json.decodeFromString(
            SongStudyCapability.serializer(),
            """{"status":"locked","reasons":[{"code":"locked","kind":"entitlement","owner_action":"buy"}]}""",
        )

        assertTrue(ready.ready)
        assertEquals(12, ready.exerciseCount)
        assertFalse(locked.ready)
        assertEquals("buy", locked.reasons.first().ownerAction)
    }

    @Test
    fun `decodes a streak leaderboard and marks the viewer`() {
        val board = json.decodeFromString(
            SongStreakLeaderboard.serializer(),
            """
            {"object":"song_streak_leaderboard","post_id":"p","community_id":"c","date":"2026-07-29",
             "entries":[
               {"rank":1,"identity":{"user_id":"usr_1","handle":"first"},"current_streak":9,
                "best_streak":12,"total_qualified_days":30,"streak_started_date":"2026-07-01",
                "last_qualified_date":"2026-07-29","is_viewer":false},
               {"rank":2,"identity":{"user_id":"usr_2"},"current_streak":4,"best_streak":4,
                "total_qualified_days":4,"streak_started_date":"2026-07-26",
                "last_qualified_date":"2026-07-29","is_viewer":true}]}
            """.trimIndent(),
        )

        assertEquals(2, board.entries.size)
        assertEquals("first", board.entries[0].identity.handle)
        assertTrue(board.entries[1].isViewer)
        assertNull(board.entries[1].identity.handle)
    }

    @Test
    fun `decodes a transcription response`() {
        val transcription = json.decodeFromString(
            SongStudyTranscriptionResponse.serializer(),
            """
            {"object":"song_study_transcription","provider":"elevenlabs","model":"scribe_v1",
             "text":"what the viewer said","confidence":0.94,"language_code":"en",
             "duration_seconds":2.5}
            """.trimIndent(),
        )

        assertEquals("what the viewer said", transcription.text)
        assertEquals(0.94, transcription.confidence!!, 0.0001)
        assertEquals("en", transcription.languageCode)
    }
}

class StudySessionGatingTest {

    /**
     * Web renders "caught up", not an error, when a payload has no exercises or no session id.
     * Modelling it as a normal state is what stops the Android screen inventing an error surface
     * for the ordinary end of a lesson.
     */
    @Test
    fun `a caught-up session carries a next due time and blocks submission`() {
        val payload = json.decodeFromString(
            SongStudyPayload.serializer(),
            """
            {"post_id":"p","community_id":"c","access":"ready","title":"t","exercise_count":0,
             "exercises":[],
             "session":{"id":null,"status":"caught_up","due_count":0,"served_count":0,
              "total_units":10,"required_correct_count":8,"max_presentations":3,
              "presentation_count":0,"completed_exercise_count":10,
              "first_pass_correct_count":9,"mastered_exercise_count":10,"qualified":true,
              "next_due_at":1753100000}}
            """.trimIndent(),
        )

        assertTrue(payload.exercises.isEmpty())
        assertEquals("caught_up", payload.session?.status)
        assertEquals(1753100000L, payload.session?.nextDueAt)
        assertFalse(payload.session?.submittable ?: true)
    }

    @Test
    fun `an active session with an id is submittable`() {
        val session = json.decodeFromString(
            SongStudySessionSummary.serializer(),
            """{"id":"ses_1","status":"active","due_count":3,"served_count":1,"total_units":8,
                "required_correct_count":6,"max_presentations":3,"presentation_count":1,
                "completed_exercise_count":0,"first_pass_correct_count":0,
                "mastered_exercise_count":0,"qualified":false}""",
        )

        assertTrue(session.submittable)
        assertNull(session.nextDueAt)
    }

    @Test
    fun `a post without study capability is not actionable`() {
        val post = json.decodeFromString(
            LocalizedPostResponse.serializer(),
            """{"post":{"id":"post_1","community":"cmt_1"}}""",
        )

        assertNull(post.studyCapability)
    }

    @Test
    fun `a post carrying a ready study capability is actionable`() {
        val post = json.decodeFromString(
            LocalizedPostResponse.serializer(),
            """{"post":{"id":"post_1","community":"cmt_1"},
                "study_capability":{"status":"ready","exercise_count":8,"target_language":"es"}}""",
        )

        assertTrue(post.studyCapability?.ready ?: false)
        assertEquals(8, post.studyCapability?.exerciseCount)
    }
}
