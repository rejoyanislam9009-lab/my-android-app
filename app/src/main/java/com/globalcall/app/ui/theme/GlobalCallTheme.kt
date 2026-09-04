package com.globalcall.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF315FEA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF0B235C),
    secondary = Color(0xFF006B8E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4E9FF),
    tertiary = Color(0xFF007A62),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF6F8FE),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EDF7),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF5C6475),
    outline = Color(0xFF8B93A7),
    outlineVariant = Color(0xFFD6DBE8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FB3FF),
    onPrimary = Color(0xFF002A76),
    primaryContainer = Color(0xFF173E9B),
    onPrimaryContainer = Color(0xFFDDE6FF),
    secondary = Color(0xFF61D3FF),
    onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF114D63),
    tertiary = Color(0xFF67E7BE),
    onTertiary = Color(0xFF00382B),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF090D14),
    surface = Color(0xFF101620),
    surfaceVariant = Color(0xFF1A2230),
    onSurface = Color(0xFFF4F6FC),
    onSurfaceVariant = Color(0xFFB9C2D3),
    outline = Color(0xFF768196),
    outlineVariant = Color(0xFF293344)
)

@Composable
fun GlobalCallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
