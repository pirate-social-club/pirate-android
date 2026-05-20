package sc.pirate.app.song

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons

@Composable
fun SongSummaryCard(
    post: LocalizedPostResponse,
    canPlay: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    body: String? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SongSummaryRow(
                post = post,
                canPlay = canPlay,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                body = body,
                artworkSize = 84.dp,
                titleStyle = MaterialTheme.typography.titleSmall,
                durationStyle = MaterialTheme.typography.bodyMedium,
                bodyStyle = MaterialTheme.typography.bodyMedium,
            )
            error?.let {
                FormNote(message = it, tone = FormTone.Error)
            }
        }
    }
}

@Composable
fun SongSummaryRow(
    post: LocalizedPostResponse,
    canPlay: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    artworkSize: Dp = 76.dp,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    durationStyle: TextStyle = MaterialTheme.typography.bodySmall,
    bodyStyle: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val title = songDisplayTitle(post)
    val coverArtSrc = resolvePublicMediaSrc(post.songPresentation?.coverArtRef)
    val duration = songDurationLabel(post.songPresentation?.durationMs)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArtwork(
            label = title,
            artworkSrc = coverArtSrc,
            size = artworkSize,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = PirateTokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            duration?.let {
                Text(
                    text = it,
                    style = durationStyle,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            body?.takeIf { it.isNotBlank() && it != title }?.let { bodyText ->
                Text(
                    text = bodyText,
                    style = bodyStyle,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            modifier = Modifier.clickable(
                enabled = canPlay,
                onClick = onPlayPause,
            ),
            shape = RoundedCornerShape(PirateTokens.radius.full),
            color = if (canPlay) PirateTokens.colors.accentBrand else PirateTokens.colors.surfaceDisabled,
        ) {
            Icon(
                imageVector = when {
                    !canPlay -> PhosphorIcons.Lock
                    isBuffering -> PhosphorIcons.MusicNotes
                    isPlaying -> PhosphorIcons.Pause
                    else -> PhosphorIcons.Play
                },
                contentDescription = when {
                    !canPlay -> "Song locked"
                    isBuffering -> "Loading song"
                    isPlaying -> "Pause song"
                    else -> "Play song"
                },
                tint = Color.White,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun SongArtwork(
    label: String,
    artworkSrc: String?,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(PirateTokens.colors.bgElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkSrc != null) {
            AsyncImage(
                model = artworkSrc,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = PhosphorIcons.MusicNote,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
            )
        }
    }
}

fun songDisplayTitle(post: LocalizedPostResponse): String =
    post.songPresentation?.title
        ?: post.post.songTitle
        ?: post.translatedTitle
        ?: post.post.title
        ?: "Untitled song"

fun songDurationLabel(durationMs: Long?): String? {
    val totalSeconds = durationMs?.takeIf { it > 0 }?.div(1000) ?: return null
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
