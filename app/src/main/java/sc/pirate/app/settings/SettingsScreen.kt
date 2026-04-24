package sc.pirate.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sc.pirate.app.ui.FeatureStubScreen

@Composable
fun SettingsScreen(
    section: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FeatureStubScreen(
        title = "Settings",
        body = "Settings section \"$section\" is not implemented yet. This route now exists so native settings work can land on a real owner.",
        modifier = modifier,
        onBack = onBack,
    )
}
