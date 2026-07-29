package sc.pirate.app.api.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val studyJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

class StudySessionContractTest {

    @Test
    fun `ready payload keeps session and scheduling fields`() {
        val payload = studyJson.decodeFromString(
            SongStudyPayload.serializer(),
            """
            {
              "post_id":"post_song",
              "community_id":"cmt_song",
              "access":"ready",
              "title":"The Stars Were On My Side",
              "exercise_count":2,
              "exercises":[
                {
                  "id":"ex_mastered",
                  "type":"say_it_back",
                  "line_id":"line_1",
                  "line_index":0,
                  "prompt_text":"Should I be",
                  "reference_text":"Should I be",
                  "max_attempts":3,
                  "presentation_count":2,
                  "mastered":true,
                  "first_outcome":"correct"
                },
                {
                  "id":"ex_due",
                  "type":"translation_choice",
                  "line_id":"line_2",
                  "line_index":1,
                  "prompt_text":"Waiting here",
                  "question":"Translate",
                  "max_attempts":3,
                  "presentation_count":1,
                  "mastered":false,
                  "first_outcome":"incorrect",
                  "options":[{"id":"opt_1","text":"Esperando aquí"}]
                }
              ],
              "session":{
                "id":"study_session_1",
                "status":"active",
                "due_count":1,
                "served_count":2,
                "total_units":10
              }
            }
            """.trimIndent(),
        )

        assertEquals("study_session_1", payload.session?.id)
        assertTrue(payload.exercises.first().mastered)
        assertEquals(1, payload.exercises.last().presentationCount)
        assertEquals("incorrect", payload.exercises.last().firstOutcome)
    }

    @Test
    fun `attempt request includes required session id and omits unrelated answer`() {
        val encoded = studyJson.encodeToString(
            SongStudyAttemptRequest.serializer(),
            SongStudyAttemptRequest(
                idempotencyKey = "idem_1",
                sessionId = "study_session_1",
                exerciseId = "ex_due",
                type = "translation_choice",
                attemptNumber = 2,
                selectedOptionId = "opt_1",
            ),
        )

        assertTrue(encoded.contains("\"session_id\":\"study_session_1\""))
        assertTrue(encoded.contains("\"attempt_number\":2"))
        assertFalse(encoded.contains("\"transcript\""))
    }

    @Test
    fun `caught up session is a normal non-submittable state`() {
        val session = studyJson.decodeFromString(
            SongStudySessionSummary.serializer(),
            """
            {
              "id":null,
              "status":"caught_up",
              "due_count":0,
              "served_count":0,
              "total_units":10,
              "next_due_at":1753100000
            }
            """.trimIndent(),
        )

        assertFalse(session.submittable)
        assertEquals(1753100000L, session.nextDueAt)
    }
}
