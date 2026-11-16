package com.pranav.duebell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF5434A5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DEFF),
    onPrimaryContainer = Color(0xFF251349),
    secondary = Color(0xFF694E81),
    secondaryContainer = Color(0xFFF3E5F7),
    onSecondaryContainer = Color(0xFF312040),
    tertiary = Color(0xFF825415),
    tertiaryContainer = Color(0xFFFFE5BD),
    onTertiaryContainer = Color(0xFF382200),
    background = Color(0xFFFAF8FC),
    surface = Color(0xFFFAF8FC),
    surfaceVariant = Color(0xFFEDE7F1),
    onSurface = Color(0xFF231C2C),
    onSurfaceVariant = Color(0xFF54495C),
    outline = Color(0xFF817587),
    outlineVariant = Color(0xFFDCD2E2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD0BBFF),
    onPrimary = Color(0xFF301D63),
    primaryContainer = Color(0xFF44307E),
    onPrimaryContainer = Color(0xFFEEE3FF),
    secondary = Color(0xFFDABFEA),
    secondaryContainer = Color(0xFF41314F),
    onSecondaryContainer = Color(0xFFF1DFFD),
    tertiary = Color(0xFFF5C679),
    tertiaryContainer = Color(0xFF5B401E),
    onTertiaryContainer = Color(0xFFFFE3BE),
    background = Color(0xFF181322),
    surface = Color(0xFF181322),
    surfaceVariant = Color(0xFF30273D),
    onSurface = Color(0xFFF0E8F4),
    onSurfaceVariant = Color(0xFFD2C6DA),
    outline = Color(0xFFAA9AB2),
    outlineVariant = Color(0xFF493E54),
)

@Composable
internal fun DueBellTheme(ringing: Boolean = false, content: @Composable () -> Unit) {
    val colors = when {
        ringing -> Dark.copy(primary = Color(0xFFF5C679), onPrimary = Color(0xFF382200))
        isSystemInDarkTheme() -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
