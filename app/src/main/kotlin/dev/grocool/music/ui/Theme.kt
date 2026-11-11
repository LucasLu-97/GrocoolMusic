/*
 * Colour palette and type scale derived from BitChord v1.9.1
 * (tag 7f45c768a461fd7e68bf4613f71e8a767e643d4c),
 * file: app/src/main/java/com/music/bitchord/ui/theme/Theme.kt
 * Copyright (C) BitChord contributors — GPL-3.0-or-later.
 *
 * Modifications for Grocool Music (2026-11-11):
 *   - package / function names changed
 *   - SF Pro Display font resources NOT copied: Apple's font is proprietary and
 *     is not redistributable under GPL. We use the platform sans-serif with the
 *     same weight-shifted scale and letter-spacing values instead. The visual
 *     rhythm is close; the exact face is intentionally not identical.
 *   - AppFont enum / legacy font variant / system-bar icon helper removed
 *   - added iOS-style glass surface tokens used by our screens
 */
package dev.grocool.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.grocool.music.ui.shape.ContinuousCornerShape
import dev.grocool.music.ui.shape.SquirclePillShape

/** Apple Music's signature red — used as an accent, not as the whole theme. */
val Accent = Color(0xFFFA2D48)

/** iOS system greys (from BitChord's DarkColors / LightColors). */
val IosSurfaceDark = Color(0xFF0D0D0F)
val IosSurfaceVariantDark = Color(0xFF1C1C1E)
val IosSecondaryLabelDark = Color(0xFF8E8E93)
val IosSeparatorDark = Color(0xFF2C2C2E)
val IosSurfaceLight = Color(0xFFF7F7F9)
val IosSurfaceVariantLight = Color(0xFFF2F2F7)
val IosSecondaryLabelLight = Color(0xFF6E6E73)
val IosSeparatorLight = Color(0xFFE5E5EA)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = IosSurfaceDark,
    onSurface = Color.White,
    surfaceVariant = IosSurfaceVariantDark,
    onSurfaceVariant = IosSecondaryLabelDark,
    outline = IosSeparatorDark,
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = IosSurfaceLight,
    onSurface = Color.Black,
    surfaceVariant = IosSurfaceVariantLight,
    onSurfaceVariant = IosSecondaryLabelLight,
    outline = IosSeparatorLight,
)

/**
 * Apple's type scale: heavy, tight large titles opening up to looser small text.
 * BitChord measured these off Apple Music; we keep the numbers but substitute
 * the platform sans-serif for the proprietary SF Pro Display files.
 */
private val GrocoolTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W800, fontSize = 30.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.25).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W600, fontSize = 16.sp, letterSpacing = 0.05.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W400, fontSize = 16.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W400, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W600, fontSize = 12.sp, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W600, fontSize = 11.sp, letterSpacing = 0.3.sp),
)

/** Reusable iOS shapes — the single biggest visual difference from Material 3. */
object IosShapes {
    val Card = ContinuousCornerShape(18.dp)
    val SmallCard = ContinuousCornerShape(12.dp)
    val LargeCard = ContinuousCornerShape(26.dp)
    val Pill = SquirclePillShape()
}

/** Semi-transparent glass token; real backdrop blur is added in a later batch. */
@Composable
fun glassSurface(): Color = if (isSystemInDarkTheme()) Color(0xB31C1C1E) else Color(0xCCF2F2F7)

@Composable
fun glassBorder(): Color = if (isSystemInDarkTheme()) Color(0x33FFFFFF) else Color(0x4DFFFFFF)

@Composable
fun secondaryLabel(): Color =
    if (isSystemInDarkTheme()) IosSecondaryLabelDark else IosSecondaryLabelLight

@Composable
fun GrocoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = GrocoolTypography,
        content = content,
    )
}
