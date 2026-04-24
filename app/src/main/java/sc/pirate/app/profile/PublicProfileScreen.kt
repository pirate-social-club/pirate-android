package sc.pirate.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sc.pirate.app.ui.FeatureStubScreen

@Composable
fun PublicProfileScreen(
    handleLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeatureStubScreen(
        title = "Public profile",
        body = "Public profile \"$handleLabel\" is not implemented yet. This route now exists as a native owner for future work.",
        modifier = modifier,
        onBack = onBack,
    )
}
