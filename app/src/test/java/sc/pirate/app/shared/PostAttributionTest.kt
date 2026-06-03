package sc.pirate.app.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.Post
import sc.pirate.app.api.model.PostDerivativeSource

class PostAttributionTest {
    @Test
    fun videoUsesSongAttributionLabel_formatsReferencesSongWithCreatorHandle() {
        val response = LocalizedPostResponse(
            post = Post(postType = "video"),
            derivativeSources = listOf(
                PostDerivativeSource(
                    title = "Sunset Driver",
                    relationshipType = "references_song",
                    creatorHandle = "lena-wave.pirate",
                ),
            ),
        )

        assertEquals(
            "Uses Sunset Driver by lena-wave.pirate",
            videoUsesSongAttributionLabel(response),
        )
    }

    @Test
    fun videoUsesSongAttributionLabel_ignoresNonVideoAndNonSongRelationships() {
        assertNull(
            videoUsesSongAttributionLabel(
                LocalizedPostResponse(
                    post = Post(postType = "song"),
                    derivativeSources = listOf(
                        PostDerivativeSource(
                            title = "Sunset Driver",
                            relationshipType = "references_song",
                            creatorHandle = "lena-wave.pirate",
                        ),
                    ),
                ),
            ),
        )
        assertNull(
            videoUsesSongAttributionLabel(
                LocalizedPostResponse(
                    post = Post(postType = "video"),
                    derivativeSources = listOf(
                        PostDerivativeSource(
                            title = "Source video",
                            relationshipType = "references_video",
                            creatorHandle = "video-maker.pirate",
                        ),
                    ),
                ),
            ),
        )
    }
}
