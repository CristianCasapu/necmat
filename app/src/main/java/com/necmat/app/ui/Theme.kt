package com.necmat.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.necmat.app.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E4F7),
    onPrimaryContainer = Color(0xFF0A2C4E),
    secondary = Color(0xFF8A6D00),
    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Color(0xFF3D2F00),
    surface = Color(0xFFFAFAFC),
    background = Color(0xFFF3F4F8),
    surfaceVariant = Color(0xFFE4E7EE),
    onSurfaceVariant = Color(0xFF44474E),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC5F0),
    onPrimary = Color(0xFF06304F),
    primaryContainer = Color(0xFF1E4468),
    onPrimaryContainer = Color(0xFFD5E4F7),
    secondary = Color(0xFFE7C64B),
    secondaryContainer = Color(0xFF574400),
    onSecondaryContainer = Color(0xFFFFE9A8),
    surface = Color(0xFF16181D),
    background = Color(0xFF101216),
    surfaceVariant = Color(0xFF2A2D34),
    onSurfaceVariant = Color(0xFFC5C8D0),
    error = Color(0xFFF2B8B5)
)

@Composable
fun NecMatTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
