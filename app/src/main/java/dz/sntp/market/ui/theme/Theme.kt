package dz.sntp.market.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val scheme = lightColorScheme(
    primary = SntpColors.DeepGreen,
    onPrimary = SntpColors.TextOnDark,
    primaryContainer = SntpColors.DeepGreen,
    onPrimaryContainer = SntpColors.TextOnDark,
    secondary = SntpColors.Amber,
    onSecondary = SntpColors.TextPrimary,
    background = SntpColors.Sand,
    onBackground = SntpColors.TextPrimary,
    surface = SntpColors.Sand,
    onSurface = SntpColors.TextPrimary,
    surfaceVariant = SntpColors.SandDim,
    onSurfaceVariant = SntpColors.TextSecondary,
    error = SntpColors.Closed,
    onError = SntpColors.TextOnDark,
    outline = SntpColors.Divider,
)

/**
 * Base type is intentionally large: the primary users are shopkeepers reading
 * a phone outdoors, often in bright sun, often not young.
 */
private val typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 52.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun SntpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = SntpColors.Amber,
                backgroundColor = SntpColors.Amber.copy(alpha = 0.3f),
            ),
            LocalContentColor provides SntpColors.TextPrimary,
            content = content,
        )
    }
}
