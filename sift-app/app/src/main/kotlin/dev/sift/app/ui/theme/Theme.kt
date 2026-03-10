package dev.sift.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF09090B),
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4F4F5),
    background       = Color(0xFFFFFFFF),
    surface          = Color(0xFFF7F7F8),
    onBackground     = Color(0xFF09090B),
    onSurface        = Color(0xFF09090B),
    outline          = Color(0xFFE4E4E7),
    secondary        = Color(0xFF52525B),
    tertiary         = Color(0xFFA1A1AA),
)

@Composable
fun SiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content     = content,
    )
}
