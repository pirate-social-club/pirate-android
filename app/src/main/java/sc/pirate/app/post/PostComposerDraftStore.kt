package sc.pirate.app.post

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sc.pirate.app.security.AndroidKeystoreSessionCipher

@Serializable
internal data class PostComposerDraftSnapshot(
    val draftIdempotencyKey: String,
    val postType: PostComposerMode,
    val selectedCommunityId: String? = null,
    val selectedCommunityName: String? = null,
    val selectedCommunityRouteSlug: String? = null,
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val live: LiveComposerState = LiveComposerState(),
    val song: SongComposerState = SongComposerState(),
    val identityMode: PostComposerIdentityMode = PostComposerIdentityMode.Public,
    val anonymousIdentityScope: String = "community_stable",
    val videoUpstreamAssetRefs: List<String> = emptyList(),
    val hadMediaSelection: Boolean = false,
)

internal class PostComposerDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cipher = AndroidKeystoreSessionCipher("pirate_post_composer_draft_v1")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): PostComposerDraftSnapshot? {
        val payload = prefs.getString(KEY_DRAFT, null) ?: return null
        return try {
            json.decodeFromString(
                PostComposerDraftSnapshot.serializer(),
                cipher.decrypt(payload),
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun save(snapshot: PostComposerDraftSnapshot) {
        val raw = json.encodeToString(PostComposerDraftSnapshot.serializer(), snapshot)
        prefs.edit().putString(KEY_DRAFT, cipher.encrypt(raw)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    private companion object {
        const val PREFS_NAME = "post_composer_draft"
        const val KEY_DRAFT = "encrypted_draft_v1"
    }
}

internal fun PostComposerUiState.toDraftSnapshot(): PostComposerDraftSnapshot = PostComposerDraftSnapshot(
    draftIdempotencyKey = draftIdempotencyKey,
    postType = postType,
    selectedCommunityId = selectedCommunityId,
    selectedCommunityName = selectedCommunityName,
    selectedCommunityRouteSlug = selectedCommunityRouteSlug,
    title = title,
    body = body,
    linkUrl = linkUrl,
    live = live,
    song = song,
    identityMode = identityMode,
    anonymousIdentityScope = anonymousIdentityScope,
    videoUpstreamAssetRefs = videoUpstreamAssetRefs,
    hadMediaSelection = listOf(
        mediaUri,
        liveCoverUri,
        songPrimaryAudioUri,
        songCoverUri,
        songCanvasVideoUri,
        songInstrumentalAudioUri,
        songVocalAudioUri,
    ).any { it != null },
)

internal fun PostComposerDraftSnapshot.restoreInto(current: PostComposerUiState): PostComposerUiState = current.copy(
    draftIdempotencyKey = draftIdempotencyKey,
    postType = postType,
    selectedCommunityId = selectedCommunityId ?: current.selectedCommunityId,
    selectedCommunityName = selectedCommunityName ?: current.selectedCommunityName,
    selectedCommunityRouteSlug = selectedCommunityRouteSlug ?: current.selectedCommunityRouteSlug,
    title = title,
    body = body,
    linkUrl = linkUrl,
    live = live.copy(coverUpload = null, coverLabel = ""),
    song = song.copy(
        primaryAudioLabel = "",
        coverLabel = "",
        canvasVideoLabel = "",
        instrumentalAudioLabel = "",
        vocalAudioLabel = "",
    ),
    identityMode = identityMode,
    anonymousIdentityScope = anonymousIdentityScope,
    videoUpstreamAssetRefs = videoUpstreamAssetRefs,
    draftNotice = if (hadMediaSelection) {
        "Draft restored. For privacy and file-access safety, reselect its media files."
    } else {
        "Draft restored."
    },
)

internal fun PostComposerUiState.hasPersistableDraft(): Boolean =
    title.isNotBlank() || body.isNotBlank() || linkUrl.isNotBlank() ||
        selectedCommunityId != null || postType != PostComposerMode.Text ||
        live != LiveComposerState() || song != SongComposerState() ||
        mediaUri != null || liveCoverUri != null || songPrimaryAudioUri != null ||
        songCoverUri != null || songCanvasVideoUri != null ||
        songInstrumentalAudioUri != null || songVocalAudioUri != null
