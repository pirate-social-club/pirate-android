package sc.pirate.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens

data class ReportReason(val code: String, val label: String)

val reportReasons = listOf(
    ReportReason("spam", "Spam"),
    ReportReason("harassment", "Harassment"),
    ReportReason("hate", "Hate"),
    ReportReason("sexual_content", "Sexual content"),
    ReportReason("graphic_content", "Graphic content"),
    ReportReason("misleading", "Misleading information"),
    ReportReason("other", "Something else"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportContentSheet(
    targetLabel: String,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onReasonSelected: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PirateTokens.colors.bgPage,
        contentColor = PirateTokens.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Report $targetLabel", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose the reason that best describes the problem.",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
            Spacer(Modifier.size(8.dp))
            reportReasons.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !submitting) { onReasonSelected(reason.code) }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PhosphorIcons.Flag, contentDescription = null)
                    Text(reason.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (submitting) Text("Submitting report…", color = PirateTokens.colors.textSecondary)
            Spacer(Modifier.size(16.dp))
        }
    }
}
