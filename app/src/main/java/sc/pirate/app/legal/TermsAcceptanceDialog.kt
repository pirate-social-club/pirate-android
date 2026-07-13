package sc.pirate.app.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens

@Composable
fun TermsAcceptanceDialog(
    state: TermsPromptState,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = { if (!state.accepting) onDismiss() },
        title = { Text("Agree before posting") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "To create or upload content, agree to Pirate's Terms of Service and acknowledge the Privacy Policy.",
                )
                TextButton(
                    onClick = { uriHandler.openUri(state.termsUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Read Terms of Service") }
                TextButton(
                    onClick = { uriHandler.openUri(state.privacyUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Read Privacy Policy") }
                state.error?.let { error ->
                    Text(error, color = PirateTokens.colors.accentDanger)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = !state.accepting) {
                Text(if (state.accepting) "Saving…" else "Agree and continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.accepting) { Text("Not now") }
        },
    )
}
