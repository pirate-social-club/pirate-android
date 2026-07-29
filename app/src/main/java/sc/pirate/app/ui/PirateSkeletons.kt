package sc.pirate.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens

@Composable
fun FeedSkeletons(count: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(count) { FeedSkeletonCard() }
    }
}

@Composable
fun FeedSkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "feed-skeleton")
    val alpha = transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeleton-alpha",
    ).value
    val shape = RoundedCornerShape(PirateTokens.radius.sm)
    val color = PirateTokens.colors.surfaceSubtle
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PirateTokens.colors.bgPage)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).background(color, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.fillMaxWidth(0.44f).height(12.dp).background(color, shape))
                Box(Modifier.fillMaxWidth(0.28f).height(10.dp).background(color, shape))
            }
        }
        Box(Modifier.fillMaxWidth(0.82f).height(19.dp).background(color, shape))
        Box(Modifier.fillMaxWidth().height(12.dp).background(color, shape))
        Box(Modifier.fillMaxWidth(0.68f).height(12.dp).background(color, shape))
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(color, shape))
        Box(Modifier.fillMaxWidth(0.36f).height(28.dp).background(color, shape))
    }
}
