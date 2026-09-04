package com.globalcall.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3568F4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF10245E),
    secondary = Color(0xFF53617D),
    tertiary = Color(0xFF0B8F78),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurface = Color(0xFF191B22),
    onSurfaceVariant = Color(0xFF44464F),
    outline = Color(0xFF767780)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF1747C8),
    onPrimaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFFBBC6E0),
    tertiary = Color(0xFF68D8C0),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111319),
    surface = Color(0xFF171920),
    surfaceVariant = Color(0xFF44464F),
    onSurface = Color(0xFFE3E2E9),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outline = Color(0xFF8F909A)
)

@Composable
fun GlobalCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
