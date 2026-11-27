package dz.sntp.market.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours, not shades. The UI asks for "background" or "closed" and the
 * active palette decides what that is; a light-only app was unreadable at 5 a.m.
 *
 * `brand` is the accent used as *text*, so it flips light in dark mode.
 * `brandBar` is the same green used as a *background* (bottom bar, toast), so it
 * stays dark in both modes. Mixing the two produced mint bars with cream text.
 */
@Immutable
data class SntpPalette(
    val bg: Color,
    val surface: Color,
    val surfaceDim: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnDark: Color,
    val onAccent: Color,
    val brand: Color,
    val brandBar: Color,
    val brandDark: Color,
    val amber: Color,
    val amberDark: Color,
    val open: Color,
    val openBg: Color,
    val closed: Color,
    val closedBg: Color,
    val unknown: Color,
    val unknownBg: Color,
)

val LightPalette = SntpPalette(
    bg = Color(0xFFF6F1E7),
    surface = Color(0xFFF6F1E7),
    surfaceDim = Color(0xFFE8E0D2),
    divider = Color(0xFFDCD3C4),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF5A5A5A),
    textOnDark = Color(0xFFF6F1E7),
    onAccent = Color(0xFFF6F1E7),
    brand = Color(0xFF0F3D3E),
    brandBar = Color(0xFF0F3D3E),
    brandDark = Color(0xFF082B2C),
    amber = Color(0xFFF2B441),
    /**
     * Amber used as *text*. The old #C98C1F was chosen to look like the accent and
     * measured 2.3:1 on the cream card — it labelled the sponsor slot ("رعاية")
     * and the "watch an ad" button, both 13sp, both effectively unreadable
     * outdoors. Darkening it is the only fix that keeps the amber family.
     */
    amberDark = Color(0xFF8A5A00),
    open = Color(0xFF197A43),
    openBg = Color(0xFFE3F4E9),
    closed = Color(0xFFB23A2E),
    closedBg = Color(0xFFFBE7E4),
    unknown = Color(0xFF7C5F16),
    unknownBg = Color(0xFFFBF2DC),
)

val DarkPalette = SntpPalette(
    bg = Color(0xFF12181A),
    surface = Color(0xFF1B2426),
    surfaceDim = Color(0xFF263134),
    divider = Color(0xFF33403F),
    textPrimary = Color(0xFFEDE8DC),
    textSecondary = Color(0xFFA9B2B3),
    textOnDark = Color(0xFFEDE8DC),
    onAccent = Color(0xFF0B1416),
    brand = Color(0xFF6FD3B8),
    brandBar = Color(0xFF0B2E2C),
    brandDark = Color(0xFF082B2C),
    amber = Color(0xFFF2B441),
    amberDark = Color(0xFFF2C063),
    open = Color(0xFF4FD07E),
    openBg = Color(0xFF12291D),
    closed = Color(0xFFF07A6B),
    closedBg = Color(0xFF2C1815),
    unknown = Color(0xFFE7BE62),
    unknownBg = Color(0xFF2A2313),
)

val LocalSntpPalette = staticCompositionLocalOf { LightPalette }
