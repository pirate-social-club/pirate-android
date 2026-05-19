package sc.pirate.app.submit

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun GlobalSubmitScreen(
    hasSession: Boolean,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSelectCommunity: (String) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val listViewModel: PostableCommunityListViewModel = viewModel()

    PostableCommunityListScreen(
        hasSession = hasSession,
        viewModel = listViewModel,
        onBack = onBack,
        onSignIn = onSignIn,
        onSelectCommunity = onSelectCommunity,
        modifier = modifier,
    )
}
