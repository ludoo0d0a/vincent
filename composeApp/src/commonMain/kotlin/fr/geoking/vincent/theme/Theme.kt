package fr.geoking.vincent.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Vincent palette — derived from the OKLCH design tokens of the Android mockups.
 * Direction: modern minimal, single lie-de-vin accent, wine-colour status hues.
 */
object VincentColors {
    val Bg = Color(0xFFF4F5F7)
    val Surface = Color(0xFFFFFFFF)
    val Surface2 = Color(0xFFF7F8FA)
    val Fg = Color(0xFF26262E)
    val Muted = Color(0xFF6E707A)
    val Faint = Color(0xFF9A9CA6)
    val Border = Color(0xFFE6E7EB)

    val Accent = Color(0xFF7E2230)        // lie-de-vin
    val AccentSoft = Color(0xFFF5E4E6)
    val AccentDeep = Color(0xFF5C1822)

    // Wine colours
    val Red = Color(0xFF9B2F2C)
    val WhiteWine = Color(0xFFC8A24A)
    val Rose = Color(0xFFD27D8E)
    val Bubbly = Color(0xFFD7C57E)

    // Status
    val Green = Color(0xFF3E9A68)
    val Amber = Color(0xFFD69A3C)
}

private val VincentColorScheme = lightColorScheme(
    primary = VincentColors.Accent,
    onPrimary = Color.White,
    primaryContainer = VincentColors.AccentSoft,
    onPrimaryContainer = VincentColors.AccentDeep,
    secondary = VincentColors.WhiteWine,
    background = VincentColors.Bg,
    onBackground = VincentColors.Fg,
    surface = VincentColors.Surface,
    onSurface = VincentColors.Fg,
    surfaceVariant = VincentColors.Surface2,
    onSurfaceVariant = VincentColors.Muted,
    outline = VincentColors.Border,
    outlineVariant = VincentColors.Border,
    error = VincentColors.Red,
)

private val VincentType = Typography().run {
    val display = FontWeight.W800
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = display, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = display, letterSpacing = (-0.4).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.W700),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.W700),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.W700),
    )
}

/** Monospace style for numerics / IDs / metadata (tabular feel). */
val MonoNumber = TextStyle(
    fontWeight = FontWeight.W700,
    fontSize = 12.sp,
    letterSpacing = 0.2.sp,
)

@Composable
fun VincentTheme(content: @Composable () -> Unit) {
    // Light only for now — the mockups are the clear/minimal direction.
    @Suppress("UNUSED_VARIABLE")
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = VincentColorScheme,
        typography = VincentType,
        content = content,
    )
}
