package sc.pirate.app.api.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PostCreateResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun communityPostCreateDecodesCanonicalPostResponse() {
        val response = """
            {
              "id": "post_pst_create_test",
              "object": "post",
              "community": "com_create_test",
              "title": "Created from Android",
              "body": "Post body",
              "post_type": "text",
              "status": "published",
              "visibility": "public",
              "identity_mode": "public",
              "analysis_state": "allow",
              "content_safety_state": "safe",
              "age_gate_policy": "none",
              "created": 1770000000
            }
        """.trimIndent()

        val post = json.decodeFromString(Post.serializer(), response)

        assertEquals("post_pst_create_test", post.postId)
        assertEquals("com_create_test", post.communityId)
        assertEquals("published", post.status)
    }
}
