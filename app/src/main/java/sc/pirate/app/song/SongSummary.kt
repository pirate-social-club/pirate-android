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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
    positionMs: Long = 0,
    durationMs: Long? = null,
    onSeek: ((Long) -> Unit)? = null,
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
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
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
    positionMs: Long = 0,
    durationMs: Long? = null,
    onSeek: ((Long) -> Unit)? = null,
) {
    val title = songDisplayTitle(post)
    val coverArtSrc = resolvePublicMediaSrc(post.songPresentation?.coverArtRef)
    val resolvedDurationMs = durationMs?.takeIf { it > 0 } ?: post.songPresentation?.durationMs?.takeIf { it > 0 }
    val resolvedPositionMs = positionMs.coerceAtLeast(0).let { position ->
        resolvedDurationMs?.let { position.coerceAtMost(it) } ?: position
    }
    val sliderMax = resolvedDurationMs?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val sliderValue = resolvedPositionMs.toFloat().coerceIn(0f, sliderMax)

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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = PirateTokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongPlayButton(
                    canPlay = canPlay,
                    isBuffering = isBuffering,
                    isPlaying = isPlaying,
                    onPlayPause = onPlayPause,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { onSeek?.invoke(it.toLong()) },
                        valueRange = 0f..sliderMax,
                        enabled = canPlay && onSeek != null && resolvedDurationMs != null,
                        colors = SliderDefaults.colors(
                            thumbColor = PirateTokens.colors.accentBrand,
                            activeTrackColor = PirateTokens.colors.accentBrand,
                            inactiveTrackColor = PirateTokens.colors.borderSoft,
                            disabledThumbColor = PirateTokens.colors.textSecondary,
                            disabledActiveTrackColor = PirateTokens.colors.borderSoft,
                            disabledInactiveTrackColor = PirateTokens.colors.borderSoft,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatSongTime(resolvedPositionMs),
                            style = durationStyle,
                            color = PirateTokens.colors.textSecondary,
                        )
                        Text(
                            text = resolvedDurationMs?.let(::formatSongTime) ?: "--:--",
                            style = durationStyle,
                            color = PirateTokens.colors.textSecondary,
                        )
                    }
                }
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
    }
}

@Composable
private fun SongPlayButton(
    canPlay: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
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
            modifier = Modifier.padding(10.dp),
        )
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
    val validDurationMs = durationMs?.takeIf { it > 0 } ?: return null
    return formatSongTime(validDurationMs)
}

fun formatSongTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
