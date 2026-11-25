package dz.sntp.market.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Named colours as composable getters so the ~80 existing call sites keep
 * working while the value follows the active palette. Anything that reads these
 * outside a composable is a bug the compiler catches.
 */
object SntpColors {
    val Sand: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.bg

    val SandDim: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.surfaceDim

    val Divider: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.divider

    val TextPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.textPrimary

    val TextSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.textSecondary

    val TextOnDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.textOnDark

    val OnAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.onAccent

    val DeepGreen: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.brand

    val BrandBar: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.brandBar

    val DeepGreenDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.brandDark

    val Amber: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.amber

    val AmberDark: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.amberDark

    val Open: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.open

    val OpenBg: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.openBg

    val Closed: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.closed

    val ClosedBg: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.closedBg

    val Unknown: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.unknown

    val UnknownBg: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSntpPalette.current.unknownBg
}
