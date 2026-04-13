package sc.pirate.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens

@Composable
fun PirateCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PirateTokens.radius.x3l),
        color = PirateTokens.colors.bgElevated,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

enum class StatusTone { Default, Success, Warning }

@Composable
fun StatusCard(
    title: String,
    description: String,
    tone: StatusTone = StatusTone.Default,
    actions: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (tone) {
        StatusTone.Success -> PirateTokens.colors.surfaceSuccess
        StatusTone.Warning -> PirateTokens.colors.surfaceWarning
        StatusTone.Default -> PirateTokens.colors.bgElevated
    }
    val borderColor = when (tone) {
        StatusTone.Success -> PirateTokens.colors.accentSuccess.copy(alpha = 0.2f)
        StatusTone.Warning -> PirateTokens.colors.accentWarning.copy(alpha = 0.2f)
        StatusTone.Default -> PirateTokens.colors.borderSoft
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PirateTokens.radius.x3l),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PirateTokens.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
            actions?.invoke()
        }
    }
}

@Composable
fun EmptyFeedState(message: String, modifier: Modifier = Modifier) {
    PirateCard(modifier = modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

enum class FormTone { Warning, Error }

@Composable
fun FormNote(message: String, tone: FormTone = FormTone.Warning, modifier: Modifier = Modifier) {
    val color = when (tone) {
        FormTone.Warning -> PirateTokens.colors.accentWarning
        FormTone.Error -> PirateTokens.colors.accentDanger
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun PirateButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(
            containerColor = PirateTokens.colors.accentBrand,
            disabledContainerColor = PirateTokens.colors.surfaceDisabled,
        ),
    ) {
        Text(text = text)
    }
}
