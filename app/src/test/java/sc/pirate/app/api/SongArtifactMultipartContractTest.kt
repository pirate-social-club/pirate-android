package sc.pirate.app.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.CreateSongArtifactUploadRequest
import sc.pirate.app.api.model.SongArtifactUpload

class SongArtifactMultipartContractTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun primaryVideoIntentRequestsDirectMultipart() {
        val encoded = json.encodeToString(
            CreateSongArtifactUploadRequest(
                artifactKind = "primary_video",
                mimeType = "video/mp4",
                filename = "video.mp4",
                sizeBytes = 1_048_576,
                uploadMode = "direct_multipart",
            ),
        )

        assertTrue(encoded.contains("\"upload_mode\":\"direct_multipart\""))
    }

    @Test
    fun multipartSessionDecodesFromUploadIntent() {
        val upload = json.decodeFromString<SongArtifactUpload>(
            """{
                "id":"sau_video",
                "artifact_kind":"primary_video",
                "upload_session":{
                    "id":"saus_video",
                    "upload_id":"filebase-video",
                    "part_size_bytes":10485760,
                    "total_parts":2
                }
            }""".trimIndent(),
        )

        assertEquals("filebase-video", upload.uploadSession?.uploadId)
        assertEquals(10_485_760L, upload.uploadSession?.partSizeBytes)
        assertEquals(2, upload.uploadSession?.totalParts)
    }
}
