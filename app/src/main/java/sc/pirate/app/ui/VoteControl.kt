package sc.pirate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier.height(38.dp),
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
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onVote(1)
                },
                enabled = enabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = PhosphorIcons.CaretUp,
                    contentDescription = "Upvote",
                    tint = if (viewerVote == 1) PirateTokens.colors.accentBrand else PirateTokens.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onVote(-1)
                },
                enabled = enabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = PhosphorIcons.CaretDown,
                    contentDescription = "Downvote",
                    tint = if (viewerVote == -1) PirateTokens.colors.accentBrand else PirateTokens.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
