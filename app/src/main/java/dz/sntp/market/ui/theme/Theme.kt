package dz.sntp.market.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private fun schemeFor(p: SntpPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.brand,
        onPrimary = p.onAccent,
        primaryContainer = p.brandBar,
        onPrimaryContainer = p.textOnDark,
        secondary = p.amber,
        onSecondary = p.onAccent,
        background = p.bg,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceDim,
        onSurfaceVariant = p.textSecondary,
        error = p.closed,
        onError = p.onAccent,
        outline = p.divider,
    )
} else {
    lightColorScheme(
        primary = p.brand,
        onPrimary = p.textOnDark,
        primaryContainer = p.brand,
        onPrimaryContainer = p.textOnDark,
        secondary = p.amber,
        onSecondary = p.textPrimary,
        background = p.bg,
        onBackground = p.textPrimary,
        surface = p.surface,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceDim,
        onSurfaceVariant = p.textSecondary,
        error = p.closed,
        onError = p.textOnDark,
        outline = p.divider,
    )
}

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

/**
 * @param darkTheme system-following by default; the settings screen can force
 * either mode, which is why this is a parameter and not `isSystemInDarkTheme()`
 * read deep in the tree.
 */
@Composable
fun SntpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme = schemeFor(palette, darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            // Icon colour must follow *our* theme, not the system one: a user who
            // forces dark on a light phone would otherwise get dark icons on a
            // dark status bar.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalSntpPalette provides palette,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = palette.amber,
            backgroundColor = palette.amber.copy(alpha = 0.3f),
        ),
        LocalContentColor provides palette.textPrimary,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
