package sc.pirate.app.moderation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sc.pirate.app.ui.FeatureStubScreen

@Composable
fun CommunityModerationScreen(
    communityId: String,
    section: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val body = if (section == null) {
        "Moderation index for community \"$communityId\" is not implemented yet."
    } else {
        "Moderation section \"$section\" for community \"$communityId\" is not implemented yet."
    }

    FeatureStubScreen(
        title = "Moderation",
        body = body,
        modifier = modifier,
        onBack = onBack,
    )
}
