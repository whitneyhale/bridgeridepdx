package pdx.bridgeride.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Decided colors (docs/design/design-system-prompt.md). Fonts come with the design system, task 3.1. */
object Pdx {
    val Ink = Color(0xFF15211D)
    val Ground = Color(0xFFF3F5F2)
    val Greenway = Color(0xFF1B6B45)
    val River = Color(0xFF1F5F8B)
    val Signal = Color(0xFFB4480F) // warnings only
    val Forest = Color(0xFF0F3D2B)
    val Rose = Color(0xFF9B1B30) // start and celebrate
    val SignalOnDark = Color(0xFFF0A070)
}

private val Light = lightColorScheme(
    primary = Pdx.Rose, onPrimary = Color.White,
    secondary = Pdx.Greenway, onSecondary = Color.White,
    tertiary = Pdx.River,
    background = Pdx.Ground, onBackground = Pdx.Ink,
    surface = Color.White, onSurface = Pdx.Ink,
    surfaceVariant = Color(0xFFE4E9E5), onSurfaceVariant = Color(0xFF3D4A44),
    error = Pdx.Signal,
)

// Dark for night riding: forest background, light text.
private val Dark = darkColorScheme(
    primary = Color(0xFFE3708A), onPrimary = Color(0xFF2B0710),
    secondary = Color(0xFF7FD3A6), onSecondary = Color(0xFF00210F),
    tertiary = Color(0xFF8CC4EE),
    background = Color(0xFF0A1F17), onBackground = Color(0xFFE6EFEA),
    surface = Pdx.Forest, onSurface = Color(0xFFE6EFEA),
    surfaceVariant = Color(0xFF1C4A38), onSurfaceVariant = Color(0xFFC3D3CB),
    error = Pdx.SignalOnDark,
)

@Composable
fun BridgeRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
