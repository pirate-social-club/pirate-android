package sc.pirate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sc.pirate.app.api.model.CrosspostSource
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.shared.resolvePublicMediaSrc
import coil.compose.AsyncImage

@Composable
fun CrosspostSourceCard(source: CrosspostSource, modifier: Modifier = Modifier) {
    CrosspostSourceCard(
        title = source.title,
        sourceCommunity = source.communityRouteSlug ?: source.communityLabel ?: source.community,
        authorLabel = source.authorLabel,
        postType = source.postType,
        status = source.status,
        thumbnailRef = source.thumbnailRef,
        modifier = modifier,
    )
}

@Composable
fun CrosspostSourceCard(
    title: String?,
    sourceCommunity: String?,
    status: String,
    modifier: Modifier = Modifier,
    authorLabel: String? = null,
    postType: String? = null,
    thumbnailRef: String? = null,
) {
    val available = status == "available"
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.md),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sourceCommunity?.let { "from $it" } ?: "Original post",
                        style = MaterialTheme.typography.labelMedium,
                        color = PirateTokens.colors.textSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    postType?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it.replaceFirstChar { character -> character.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = PirateTokens.colors.textSecondary,
                        )
                    }
                }
                Text(
                    text = if (available) title?.takeIf { it.isNotBlank() } ?: "Untitled post" else "Source post unavailable",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (available) PirateTokens.colors.textPrimary else PirateTokens.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                authorLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "by $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            resolvePublicMediaSrc(thumbnailRef)?.let { thumbnailUrl ->
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(PirateTokens.radius.sm)),
                )
            }
        }
    }
}
