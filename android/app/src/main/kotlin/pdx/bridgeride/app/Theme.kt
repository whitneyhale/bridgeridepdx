package pdx.bridgeride.app

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Two looks, decided 2026-11-09 from the "Five Design Directions" canvas:
 * light is E (Portland Explorer), dark is C (Modern Urban Cyclist).
 * Checked by tools/check_palette.py against docs/design/colors-e.json and colors-c.json.
 */
object PdxE {
    val Cream = Color(0xFFFFF5E1)
    val Ink = Color(0xFF1D2B4F)
    val Muted = Color(0xFF3A4767)
    val Teal = Color(0xFF0E7C74) // route, primary actions
    val Fern = Color(0xFF3F7D3A) // greenways, good states
    val Rose = Color(0xFFC2305F) // picks
    val Mustard = Color(0xFFF2B705) // badges, bridge dots
    val Sand = Color(0xFFFBE3BC)
    // Canvas value was #D9480F (white text 4.30:1). Darkened a hair so white text reaches 4.5:1.
    val Hazard = Color(0xFFD44304)
}

object PdxC {
    val Asphalt = Color(0xFF0B0B0C)
    val Concrete = Color(0xFF18181A)
    val Line = Color(0xFF2C2C30)
    val Steel = Color(0xFF9A9AA0)
    val White = Color(0xFFF5F5F5)
    val Green = Color(0xFF22C55E) // route, primary actions
    val Cyan = Color(0xFF3DD6F5) // greenways, good states
    val Signal = Color(0xFFFF5A47) // warnings
}

private val LightE = lightColorScheme(
    primary = PdxE.Teal, onPrimary = Color.White,
    secondary = PdxE.Fern, onSecondary = Color.White,
    tertiary = PdxE.Rose, onTertiary = Color.White,
    background = PdxE.Cream, onBackground = PdxE.Ink,
    surface = Color.White, onSurface = PdxE.Ink,
    surfaceVariant = PdxE.Sand, onSurfaceVariant = PdxE.Muted,
    surfaceContainer = Color.White,
    secondaryContainer = PdxE.Mustard, onSecondaryContainer = PdxE.Ink,
    outline = PdxE.Ink, outlineVariant = PdxE.Sand,
    error = PdxE.Hazard, onError = Color.White,
)

private val DarkC = darkColorScheme(
    primary = PdxC.Green, onPrimary = PdxC.Asphalt,
    secondary = PdxC.Cyan, onSecondary = PdxC.Asphalt,
    tertiary = PdxC.White, onTertiary = PdxC.Asphalt,
    background = PdxC.Asphalt, onBackground = PdxC.White,
    surface = PdxC.Concrete, onSurface = PdxC.White,
    surfaceVariant = PdxC.Line, onSurfaceVariant = PdxC.Steel,
    surfaceContainer = PdxC.Asphalt,
    secondaryContainer = PdxC.Green, onSecondaryContainer = PdxC.Asphalt,
    outline = Color(0xFF3A3A40), outlineVariant = PdxC.Line,
    error = PdxC.Signal, onError = PdxC.Asphalt,
)

/** What differs between the looks beyond color: shapes, borders, number font, map colors. */
@Immutable
data class Look(
    val dark: Boolean,
    val corner: Dp,
    val border: Dp,
    val borderColor: Color,
    /** E cards and buttons sit on a hard ink shadow, like a sticker. C has none. */
    val stickerShadow: Dp,
    val numbers: FontFamily,
    val mapStyle: String,
    // Bridge dots never use the route color (safety rule, 2026-11-06).
    val bridgeDot: String,
    val bridgeDotRing: String,
    val bridgeDotClosed: String,
    // The line being followed: solid with an outline, so it never depends on color alone (palette fix 1).
    val route: String,
    val routeCasing: String,
    // The bike marker. "Est" is the estimated-position version, in the warning color.
    val markerFill: Int,
    val markerRing: Int,
    val markerBike: Int,
    val markerEstFill: Int,
    val markerEstRing: Int,
    val markerEstBike: Int,
    val markerPointerEdge: Int,
)

val LocalLook = staticCompositionLocalOf<Look> { error("BridgeRideTheme is missing") }

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
    weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
)

private val Bricolage = variable(R.font.bricolage_grotesque, 700, 800)
private val DmSans = variable(R.font.dm_sans, 400, 500, 700)
private val Archivo = variable(R.font.archivo, 400, 600, 800)
private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.Bold),
)

private fun typography(heading: FontFamily, body: FontFamily, headingWeight: FontWeight): Typography {
    val base = Typography()
    fun TextStyle.h() = copy(fontFamily = heading, fontWeight = headingWeight)
    fun TextStyle.b() = copy(fontFamily = body)
    return Typography(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(), headlineSmall = base.headlineSmall.h(),
        titleLarge = base.titleLarge.h().copy(fontSize = 22.sp), titleMedium = base.titleMedium.h(), titleSmall = base.titleSmall.h(),
        bodyLarge = base.bodyLarge.b(), bodyMedium = base.bodyMedium.b(), bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b(), labelMedium = base.labelMedium.b(), labelSmall = base.labelSmall.b(),
    )
}

private val TypeE = typography(Bricolage, DmSans, FontWeight.ExtraBold)
private val TypeC = typography(Archivo, Archivo, FontWeight.ExtraBold)

private val LookE = Look(
    dark = false, corner = 18.dp, border = 2.5.dp, borderColor = PdxE.Ink, stickerShadow = 4.dp,
    numbers = Bricolage, mapStyle = "asset://style_e.json",
    bridgeDot = "#F2B705", bridgeDotRing = "#1D2B4F", bridgeDotClosed = "#B9B4A6",
    route = "#0E7C74", routeCasing = "#1D2B4F",
    markerFill = 0xFF0E7C74.toInt(), markerRing = 0xFF1D2B4F.toInt(), markerBike = 0xFFFFFFFF.toInt(),
    markerEstFill = 0xFFD44304.toInt(), markerEstRing = 0xFF1D2B4F.toInt(), markerEstBike = 0xFFFFFFFF.toInt(),
    markerPointerEdge = 0xFFFFF5E1.toInt(),
)
private val LookC = Look(
    dark = true, corner = 2.dp, border = 1.dp, borderColor = PdxC.Line, stickerShadow = 0.dp,
    numbers = PlexMono, mapStyle = "asset://style_c.json",
    bridgeDot = "#F5F5F5", bridgeDotRing = "#0B0B0C", bridgeDotClosed = "#5A5A60",
    route = "#22C55E", routeCasing = "#000000",
    markerFill = 0xFF0B0B0C.toInt(), markerRing = 0xFF22C55E.toInt(), markerBike = 0xFF22C55E.toInt(),
    markerEstFill = 0xFF0B0B0C.toInt(), markerEstRing = 0xFFFF5A47.toInt(), markerEstBike = 0xFFFF5A47.toInt(),
    markerPointerEdge = 0xFF0B0B0C.toInt(),
)

/** The rider's choice in Settings. SYSTEM follows the phone's dark mode. */
enum class LookChoice(val label: String) { SYSTEM("Auto"), LIGHT("Light"), DARK("Dark") }

object LookPrefs {
    private const val FILE = "prefs"
    private const val KEY = "look"
    private val state = MutableStateFlow(LookChoice.SYSTEM)
    val choice: StateFlow<LookChoice> = state

    fun load(context: Context) {
        val saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        state.value = LookChoice.entries.firstOrNull { it.name == saved } ?: LookChoice.SYSTEM
    }

    fun set(context: Context, choice: LookChoice) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, choice.name).apply()
        state.value = choice
    }
}

@Composable
fun BridgeRideTheme(content: @Composable () -> Unit) {
    val choice by LookPrefs.choice.collectAsStateWithLifecycle()
    val dark = when (choice) {
        LookChoice.SYSTEM -> isSystemInDarkTheme()
        LookChoice.LIGHT -> false
        LookChoice.DARK -> true
    }
    // Status and navigation bar icons must follow the look, not the phone setting.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalLook provides if (dark) LookC else LookE) {
        MaterialTheme(
            colorScheme = if (dark) DarkC else LightE,
            typography = if (dark) TypeC else TypeE,
            content = content,
        )
    }
}
