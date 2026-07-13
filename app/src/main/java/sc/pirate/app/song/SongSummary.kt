package sc.pirate.app.song

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
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
                titleStyle = MaterialTheme.typography.titleMedium,
                durationStyle = MaterialTheme.typography.bodySmall,
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
                maxLines = 1,
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    SongSeekBar(
                        positionMs = resolvedPositionMs,
                        durationMs = resolvedDurationMs,
                        enabled = canPlay && onSeek != null,
                        onSeek = { position -> onSeek?.invoke(position) },
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
                        resolvedDurationMs?.let { duration ->
                            Text(
                                text = formatSongTime(duration),
                                style = durationStyle,
                                color = PirateTokens.colors.textSecondary,
                            )
                        }
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
private fun SongSeekBar(
    positionMs: Long,
    durationMs: Long?,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
) {
    val duration = durationMs?.takeIf { it > 0 }
    val seekEnabled = enabled && duration != null
    val progress = duration?.let { positionMs.coerceIn(0, it).toFloat() / it.toFloat() } ?: 0f
    val activeColor = PirateTokens.colors.accentBrand
    val inactiveColor = PirateTokens.colors.borderSoft
    val disabledThumbColor = PirateTokens.colors.textSecondary
    val semanticsMax = duration?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = positionMs.toFloat().coerceIn(0f, semanticsMax),
                    range = 0f..semanticsMax,
                )
                if (seekEnabled) {
                    setProgress { target ->
                        onSeek(target.toLong().coerceIn(0, duration!!))
                        true
                    }
                }
            }
            .pointerInput(seekEnabled, duration) {
                if (seekEnabled) {
                    detectTapGestures { offset ->
                        onSeek(songSeekPosition(offset.x, size.width.toFloat(), duration!!, 5.dp.toPx()))
                    }
                }
            }
            .pointerInput(seekEnabled, duration) {
                if (seekEnabled) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onSeek(songSeekPosition(change.position.x, size.width.toFloat(), duration!!, 5.dp.toPx()))
                    }
                }
            },
    ) {
        val thumbRadius = 5.dp.toPx()
        val trackStart = thumbRadius
        val trackEnd = (size.width - thumbRadius).coerceAtLeast(trackStart)
        val progressX = trackStart + (trackEnd - trackStart) * progress
        val centerY = size.height / 2f
        val strokeWidth = 2.dp.toPx()

        drawLine(
            color = inactiveColor,
            start = androidx.compose.ui.geometry.Offset(trackStart, centerY),
            end = androidx.compose.ui.geometry.Offset(trackEnd, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        if (progressX > trackStart) {
            drawLine(
                color = activeColor,
                start = androidx.compose.ui.geometry.Offset(trackStart, centerY),
                end = androidx.compose.ui.geometry.Offset(progressX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = if (seekEnabled) activeColor else disabledThumbColor,
            radius = thumbRadius,
            center = androidx.compose.ui.geometry.Offset(progressX, centerY),
        )
    }
}

internal fun songSeekPosition(pointerX: Float, width: Float, durationMs: Long, thumbRadiusPx: Float): Long {
    if (width <= 0f || durationMs <= 0) return 0
    val trackStart = thumbRadiusPx.coerceIn(0f, width / 2f)
    val trackWidth = (width - 2f * trackStart).coerceAtLeast(0.0001f)
    val progress = ((pointerX - trackStart) / trackWidth).coerceIn(0f, 1f)
    return (durationMs * progress).toLong().coerceIn(0, durationMs)
}

@Composable
private fun SongPlayButton(
    canPlay: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(
                enabled = canPlay,
                onClick = onPlayPause,
            ),
        shape = RoundedCornerShape(PirateTokens.radius.full),
        color = if (canPlay) PirateTokens.colors.accentBrand else PirateTokens.colors.surfaceDisabled,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                modifier = Modifier.size(19.dp),
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
    val validDurationMs = durationMs?.takeIf { it > 0 } ?: return null
    return formatSongTime(validDurationMs)
}

fun formatSongTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
