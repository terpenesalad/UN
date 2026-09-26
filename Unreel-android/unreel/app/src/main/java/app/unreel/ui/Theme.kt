package app.unreel.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBE1),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B),
    background = Color(0xFFF7F6F1),
    onBackground = Color(0xFF1B1C1A),
    surface = Color(0xFFF7F6F1),
    onSurface = Color(0xFF1B1C1A),
    surfaceVariant = Color(0xFFE6E4DC),
    onSurfaceVariant = Color(0xFF55584F),
    surfaceContainer = Color(0xFFEFEDE6),
    surfaceContainerHighest = Color(0xFFE8E6DF),
    errorContainer = Color(0xFFFBE3DD),
    onErrorContainer = Color(0xFF5C1A0F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD5BF),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFABF2DA),
    secondary = Color(0xFFB2CCC2),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E3DE),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE3E3DE),
    surfaceVariant = Color(0xFF2A2D2A),
    onSurfaceVariant = Color(0xFFC0C4BD),
    surfaceContainer = Color(0xFF1C1F1C),
    surfaceContainerHighest = Color(0xFF2A2D2A),
    errorContainer = Color(0xFF5C1A0F),
    onErrorContainer = Color(0xFFFFDAD2),
)

@Composable
fun UnreelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
