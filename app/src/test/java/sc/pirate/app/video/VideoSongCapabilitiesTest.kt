package sc.pirate.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test
import sc.pirate.app.api.model.DerivativeSource
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.Post
import sc.pirate.app.api.model.SongKaraokeCapability
import sc.pirate.app.api.model.SongStudyCapability

private fun songReference(
    sourcePost: String? = "post_song_1",
    relationship: String? = "references_song",
    community: String = "com_cmt_1",
) = DerivativeSource(
    sourceRef = "story:ip:0xabc#licenseTermsId=1",
    relationshipType = relationship,
    sourcePost = sourcePost,
    community = community,
    kind = "song",
    title = "A Song",
)

private fun videoPost(sources: List<DerivativeSource>) =
    LocalizedPostResponse(post = Post(), rawDerivativeSources = sources)

private fun songPost(
    study: String? = null,
    karaoke: String? = null,
    ageGate: String? = null,
) = LocalizedPostResponse(
    post = Post(),
    studyCapability = study?.let { SongStudyCapability(status = it) },
    karaokeCapability = karaoke?.let { SongKaraokeCapability(status = it) },
    ageGateViewerState = ageGate,
)

class ReferencedSongTest {

    @Test
    fun `finds the song a video references`() {
        val found = referencedSong(videoPost(listOf(songReference())))

        assertEquals("post_song_1", found?.sourcePost)
    }

    @Test
    fun `a video with no attributions references nothing`() {
        assertNull(referencedSong(videoPost(emptyList())))
    }

    /** Remixes and video references are attributions too; only a song reference drives the rail. */
    @Test
    fun `ignores attributions that are not song references`() {
        val post = videoPost(
            listOf(
                songReference(relationship = "remix_of"),
                songReference(relationship = "references_video"),
            ),
        )

        assertNull(referencedSong(post))
    }

    /**
     * The attribution can name a Story IP without naming a post. There is nothing to fetch in that
     * case, so it must not be treated as a resolvable reference.
     */
    @Test
    fun `ignores a reference with no source post`() {
        assertNull(referencedSong(videoPost(listOf(songReference(sourcePost = null)))))
        assertNull(referencedSong(videoPost(listOf(songReference(sourcePost = "")))))
    }

    @Test
    fun `picks the song reference out of a mixed list`() {
        val post = videoPost(
            listOf(songReference(relationship = "inspired_by"), songReference(sourcePost = "post_song_9")),
        )

        assertEquals("post_song_9", referencedSong(post)?.sourcePost)
    }
}

class ResolveVideoSongCapabilitiesTest {

    @Test
    fun `a ready song offers both actions`() {
        val r = resolveVideoSongCapabilities("post_song_1", "com_cmt_1", songPost(study = "ready", karaoke = "ready"))

        assertTrue(r.studyReady)
        assertTrue(r.karaokeReady)
        assertEquals("post_song_1", r.songPostId)
        assertEquals("com_cmt_1", r.songCommunityId)
    }

    @Test
    fun `each capability is independent`() {
        val studyOnly = resolveVideoSongCapabilities("p", "c", songPost(study = "ready", karaoke = "processing"))
        val singOnly = resolveVideoSongCapabilities("p", "c", songPost(study = "locked", karaoke = "ready"))

        assertTrue(studyOnly.studyReady); assertFalse(studyOnly.karaokeReady)
        assertFalse(singOnly.studyReady); assertTrue(singOnly.karaokeReady)
    }

    @Test
    fun `only ready counts`() {
        listOf("locked", "processing", "unavailable", "failed").forEach { status ->
            val r = resolveVideoSongCapabilities("p", "c", songPost(study = status, karaoke = status))
            assertFalse("status $status should not be actionable", r.studyReady || r.karaokeReady)
        }
    }

    @Test
    fun `absent capabilities offer nothing`() {
        val r = resolveVideoSongCapabilities("p", "c", songPost())

        assertFalse(r.studyReady)
        assertFalse(r.karaokeReady)
    }

    /**
     * Offering an action the destination screen will refuse is worse than not offering it: the
     * viewer taps, gets bounced, and learns the rail lies.
     */
    @Test
    fun `an age-gated song offers nothing even when both capabilities are ready`() {
        val r = resolveVideoSongCapabilities(
            "p", "c",
            songPost(study = "ready", karaoke = "ready", ageGate = "proof_required"),
        )

        assertFalse(r.studyReady)
        assertFalse(r.karaokeReady)
    }

    /** The attribution usually names the community; fall back to the song post's own. */
    @Test
    fun `falls back to the song post community when the attribution omits it`() {
        // Post's community fields are private constructor params, so build it the way the API
        // does — from JSON.
        val song = Json { ignoreUnknownKeys = true }.decodeFromString(
            LocalizedPostResponse.serializer(),
            """{"post":{"id":"post_1","community":"com_cmt_from_post"},
                "study_capability":{"status":"ready"}}""",
        )

        assertEquals("com_cmt_from_post", resolveVideoSongCapabilities("p", "", song).songCommunityId)
    }
}

class DerivativeSourcesNullTolerangeTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The API sends an explicit `"derivative_sources": null` for posts with no attributions.
     * Declaring it as a non-null list with a default rejects that outright, which took the entire
     * feed down rather than one field — the whole surface showed a decode error.
     */
    @Test
    fun `an explicit null decodes to no attributions`() {
        val post = json.decodeFromString(
            LocalizedPostResponse.serializer(),
            """{"post":{"id":"p","community":"c"},"derivative_sources":null,"upvote_count":9}""",
        )

        assertTrue(post.derivativeSources.isEmpty())
        assertNull(referencedSong(post))
    }

    @Test
    fun `an absent field decodes to no attributions`() {
        val post = json.decodeFromString(
            LocalizedPostResponse.serializer(),
            """{"post":{"id":"p","community":"c"}}""",
        )

        assertTrue(post.derivativeSources.isEmpty())
    }

    @Test
    fun `a populated list still resolves the song reference`() {
        val post = json.decodeFromString(
            LocalizedPostResponse.serializer(),
            """{"post":{"id":"p","community":"c"},"derivative_sources":[
                {"source_ref":"story:ip:0xabc#licenseTermsId=1894","title":"The Stars Were On My Side",
                 "kind":"song","relationship_type":"references_song","community":"com_cmt_f",
                 "source_post":"post_pst_f602adc4"}]}""",
        )

        assertEquals("post_pst_f602adc4", referencedSong(post)?.sourcePost)
        assertEquals("com_cmt_f", referencedSong(post)?.community)
    }
}
