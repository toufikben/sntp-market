package dz.sntp.market.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette chosen for a market at 5 a.m. under street lights: high contrast,
 * no pure white, and a single accent per state so a glance is enough.
 */
object SntpColors {
    val DeepGreen = Color(0xFF0F3D3E)
    val DeepGreenDark = Color(0xFF082B2C)
    val Sand = Color(0xFFF6F1E7)
    val SandDim = Color(0xFFE8E0D2)
    val Amber = Color(0xFFF2B441)
    val AmberDark = Color(0xFFC98C1F)

    val Open = Color(0xFF1E8E4E)
    val OpenBg = Color(0xFFE3F4E9)
    val Closed = Color(0xFFB23A2E)
    val ClosedBg = Color(0xFFFBE7E4)
    val Unknown = Color(0xFF8A6D1F)
    val UnknownBg = Color(0xFFFBF2DC)

    val TextPrimary = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF5A5A5A)
    val TextOnDark = Color(0xFFF6F1E7)
    val Divider = Color(0xFFDCD3C4)

    // ------------------------------------------------------------- night
    // Traders check this before dawn and after the gate closes. A white screen
    // at 5 a.m. in a dark car is physically painful, so dark is a real mode,
    // not a theme checkbox.
    val NightBg = Color(0xFF12181A)
    val NightSurface = Color(0xFF1B2426)
    val NightSurfaceDim = Color(0xFF263134)
    val NightText = Color(0xFFEDE8DC)
    val NightTextSecondary = Color(0xFFA9B2B3)
    val NightDivider = Color(0xFF33403F)
    val NightOpen = Color(0xFF4FD07E)
    val NightOpenBg = Color(0xFF12291D)
    val NightClosed = Color(0xFFF07A6B)
    val NightClosedBg = Color(0xFF2C1815)
    val NightUnknown = Color(0xFFE7BE62)
    val NightUnknownBg = Color(0xFF2A2313)
}
