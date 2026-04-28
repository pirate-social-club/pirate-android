package sc.pirate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens

@Composable
fun VoteControl(
    score: Int,
    viewerVote: Int?,
    enabled: Boolean,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PirateTokens.radius.full),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(
                onClick = { onVote(1) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = PhosphorIcons.CaretUp,
                    contentDescription = "Upvote",
                    tint = if (viewerVote == 1) PirateTokens.colors.accentBrand else PirateTokens.colors.textSecondary,
                )
            }
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
            IconButton(
                onClick = { onVote(-1) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = PhosphorIcons.CaretDown,
                    contentDescription = "Downvote",
                    tint = if (viewerVote == -1) PirateTokens.colors.accentBrand else PirateTokens.colors.textSecondary,
                )
            }
        }
    }
}
