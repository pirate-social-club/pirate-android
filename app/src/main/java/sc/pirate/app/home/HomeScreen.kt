package sc.pirate.app.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import sc.pirate.app.theme.PirateTokens

@Composable
fun HomeScreen(
    onNavigateToCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Welcome to Pirate",
            style = MaterialTheme.typography.headlineMedium,
            color = PirateTokens.colors.textPrimary,
        )
    }
}
