package sc.pirate.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DarkBackground = Color(0xFF262624)
private val DarkForeground = Color(0xFFC3C0B6)
private val DarkElevated = Color(0xFF30302E)
private val DarkPrimary = Color(0xFFD97757)
private val DarkMuted = Color(0xFF1B1B19)
private val DarkMutedForeground = Color(0xFFB7B5A9)
private val DarkBorder = Color(0xFF3E3E38)
private val DarkInput = Color(0xFF52514A)
private val DarkDestructive = Color(0xFFEF4444)

private fun surfaceFrom(base: Color, alpha: Float, backdrop: Color = DarkBackground): Color =
    base.copy(alpha = alpha).compositeOver(backdrop)

@Immutable
data class PirateColorTokens(
    val bgPage: Color,
    val bgSurface: Color,
    val bgElevated: Color,
    val bgOverlay: Color,
    val surfaceHoverSubtle: Color,
    val surfaceSubtle: Color,
    val surfaceInteractive: Color,
    val surfaceSkeleton: Color,
    val surfaceDisabled: Color,
    val surfaceAccent: Color,
    val surfaceDanger: Color,
    val surfaceSuccess: Color,
    val surfaceWarning: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val textOnAccent: Color,
    val borderDefault: Color,
    val borderSoft: Color,
    val borderStrong: Color,
    val accentBrand: Color,
    val accentDanger: Color,
    val accentSuccess: Color,
    val accentWarning: Color,
    val input: Color,
)

@Immutable
data class PirateRadiusTokens(
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val x2l: Dp,
    val x3l: Dp,
    val full: Dp,
)

private val PirateDarkColors =
    PirateColorTokens(
        bgPage = DarkBackground,
        bgSurface = DarkBackground,
        bgElevated = DarkElevated,
        bgOverlay = Color.Black.copy(alpha = 0.55f),
        surfaceHoverSubtle = surfaceFrom(DarkMuted, 0.08f),
        surfaceSubtle = surfaceFrom(DarkMuted, 0.24f),
        surfaceInteractive = surfaceFrom(DarkMuted, 0.48f),
        surfaceSkeleton = surfaceFrom(DarkMuted, 0.62f),
        surfaceDisabled = surfaceFrom(DarkMuted, 0.78f),
        surfaceAccent = surfaceFrom(DarkPrimary, 0.10f),
        surfaceDanger = surfaceFrom(DarkDestructive, 0.08f),
        surfaceSuccess = surfaceFrom(Color(0xFF34D399), 0.08f),
        surfaceWarning = surfaceFrom(Color(0xFFF59E0B), 0.08f),
        textPrimary = DarkForeground,
        textSecondary = DarkMutedForeground,
        textDisabled = DarkMutedForeground.copy(alpha = 0.45f),
        textOnAccent = Color.White,
        borderDefault = DarkBorder,
        borderSoft = DarkBorder.copy(alpha = 0.70f),
        borderStrong = DarkInput,
        accentBrand = DarkPrimary,
        accentDanger = DarkDestructive,
        accentSuccess = Color(0xFF34D399),
        accentWarning = Color(0xFFF59E0B),
        input = DarkInput,
    )

private val PirateRadiusScale =
    PirateRadiusTokens(
        sm = 4.dp,
        md = 8.dp,
        lg = 12.dp,
        xl = 14.dp,
        x2l = 16.dp,
        x3l = 24.dp,
        full = 999.dp,
    )

internal val LocalPirateColors = staticCompositionLocalOf<PirateColorTokens> {
    error("Pirate color tokens not provided")
}

internal val LocalPirateRadii = staticCompositionLocalOf<PirateRadiusTokens> {
    error("Pirate radius tokens not provided")
}

object PirateTokens {
    val darkColors: PirateColorTokens = PirateDarkColors
    val radii: PirateRadiusTokens = PirateRadiusScale

    val colors: PirateColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalPirateColors.current

    val radius: PirateRadiusTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalPirateRadii.current
}
