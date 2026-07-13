package sc.pirate.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

private val PirateShapes = Shapes().copy(
    small = RoundedCornerShape(PirateTokens.radii.lg),
    medium = RoundedCornerShape(PirateTokens.radii.xl),
    large = RoundedCornerShape(PirateTokens.radii.xl),
    extraLarge = RoundedCornerShape(PirateTokens.radii.x2l),
)

private val PirateTypography = Typography().copy(
    labelLarge = Typography().labelLarge.copy(fontSize = 16.sp),
)

private val PirateDarkColors = darkColorScheme(
    background = PirateTokens.darkColors.bgPage,
    onBackground = PirateTokens.darkColors.textPrimary,
    surface = PirateTokens.darkColors.bgSurface,
    onSurface = PirateTokens.darkColors.textPrimary,
    surfaceVariant = PirateTokens.darkColors.surfaceInteractive,
    onSurfaceVariant = PirateTokens.darkColors.textSecondary,
    primary = PirateTokens.darkColors.accentBrand,
    onPrimary = PirateTokens.darkColors.textOnAccent,
    primaryContainer = PirateTokens.darkColors.surfaceAccent,
    onPrimaryContainer = PirateTokens.darkColors.accentBrand,
    secondary = PirateTokens.darkColors.textSecondary,
    onSecondary = PirateTokens.darkColors.bgPage,
    secondaryContainer = PirateTokens.darkColors.surfaceInteractive,
    onSecondaryContainer = PirateTokens.darkColors.textPrimary,
    tertiary = PirateTokens.darkColors.accentSuccess,
    onTertiary = Color(0xFF102014),
    tertiaryContainer = PirateTokens.darkColors.surfaceSubtle,
    onTertiaryContainer = PirateTokens.darkColors.accentSuccess,
    error = PirateTokens.darkColors.accentDanger,
    onError = PirateTokens.darkColors.textOnAccent,
    errorContainer = PirateTokens.darkColors.surfaceDanger,
    onErrorContainer = PirateTokens.darkColors.accentDanger,
    outline = PirateTokens.darkColors.borderDefault,
    outlineVariant = PirateTokens.darkColors.borderSoft,
)

private val PirateLightColors = lightColorScheme(
    background = PirateTokens.lightColors.bgPage,
    onBackground = PirateTokens.lightColors.textPrimary,
    surface = PirateTokens.lightColors.bgSurface,
    onSurface = PirateTokens.lightColors.textPrimary,
    surfaceVariant = PirateTokens.lightColors.surfaceInteractive,
    onSurfaceVariant = PirateTokens.lightColors.textSecondary,
    primary = PirateTokens.lightColors.accentBrand,
    onPrimary = PirateTokens.lightColors.textOnAccent,
    primaryContainer = PirateTokens.lightColors.surfaceAccent,
    onPrimaryContainer = PirateTokens.lightColors.accentBrand,
    secondary = PirateTokens.lightColors.textSecondary,
    onSecondary = PirateTokens.lightColors.bgPage,
    secondaryContainer = PirateTokens.lightColors.surfaceInteractive,
    onSecondaryContainer = PirateTokens.lightColors.textPrimary,
    tertiary = PirateTokens.lightColors.accentSuccess,
    onTertiary = Color.White,
    tertiaryContainer = PirateTokens.lightColors.surfaceSuccess,
    onTertiaryContainer = PirateTokens.lightColors.accentSuccess,
    error = PirateTokens.lightColors.accentDanger,
    onError = PirateTokens.lightColors.textOnAccent,
    errorContainer = PirateTokens.lightColors.surfaceDanger,
    onErrorContainer = PirateTokens.lightColors.accentDanger,
    outline = PirateTokens.lightColors.borderDefault,
    outlineVariant = PirateTokens.lightColors.borderSoft,
)

@Composable
fun PirateTheme(
    appearanceMode: AppearanceMode = AppearanceMode.System,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = appearanceMode.usesDarkTheme(isSystemInDarkTheme())
    val tokens = if (useDarkTheme) PirateTokens.darkColors else PirateTokens.lightColors
    CompositionLocalProvider(
        LocalPirateColors provides tokens,
        LocalPirateRadii provides PirateTokens.radii,
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) PirateDarkColors else PirateLightColors,
            shapes = PirateShapes,
            typography = PirateTypography,
            content = content,
        )
    }
}
