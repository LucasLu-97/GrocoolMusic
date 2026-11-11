package dev.grocool.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Apple Music 风格强调色；仅作风格参考，不使用其图标、字体或商标资源。 */
val Accent = Color(0xFFFA233B)
val LightBackground = Color(0xFFF5F5F7)
val DarkBackground = Color(0xFF121214)

@Composable
fun GrocoolTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(primary = Accent, background = DarkBackground, surface = Color(0xFF1C1C1E))
        } else {
            lightColorScheme(primary = Accent, background = LightBackground, surface = Color.White)
        },
        content = content,
    )
}
