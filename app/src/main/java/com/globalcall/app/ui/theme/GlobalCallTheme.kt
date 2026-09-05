package com.globalcall.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object ThemePreferences {
    private const val PREFS = "globalcall_ui_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private var initialized = false

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    fun initialize(context: Context) {
        if (initialized) return
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        mode = runCatching { ThemeMode.valueOf(stored ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        initialized = true
    }

    fun setMode(context: Context, newMode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, newMode.name)
            .apply()
        mode = newMode
        initialized = true
    }
}

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
    val context = LocalContext.current.applicationContext
    remember(context) {
        ThemePreferences.initialize(context)
        true
    }
    val useDark = when (ThemePreferences.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
