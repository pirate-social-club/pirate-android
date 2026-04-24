package sc.pirate.app.submit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sc.pirate.app.ui.FeatureStubScreen

@Composable
fun GlobalSubmitScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeatureStubScreen(
        title = "Submit",
        body = "Global submit is not implemented yet. Native flow should let the user choose a community before opening the composer.",
        modifier = modifier,
        onBack = onBack,
    )
}
