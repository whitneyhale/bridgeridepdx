package pdx.bridgeride.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The 14 bridge icons from the "Five Design Directions" canvas (BridgeSet), one shape per bridge
 * on a 96 x 48 grid: f is the filled silhouette, s the main strokes, a the accent (trim color),
 * d2 and d3 finer detail shown only when the icon is large enough to read it.
 * Colors are the bridges' own paint (data/geometry.json); they are never the route color.
 */
private class Art(
    val color: Long, val trim: Long? = null,
    val f: String, val s: String, val a: String = "", val d2: String = "", val d3: String = "",
    /** Blumenauer crosses a road, not water: dashed ground line instead of waves. */
    val road: Boolean = false,
)

private const val WAVES = "M4 43 q6 -3 12 0 t12 0 t12 0 t12 0 t12 0 t12 0 t12 0"
private const val ROAD = "M4 43 H92"

private val ART: Map<String, Art> = mapOf(
    "st-johns" to Art(
        0xFF2E7D5B,
        f = "M20 34 V12 L24 4 L28 12 V34 Z M68 34 V12 L72 4 L76 12 V34 Z",
        s = "M4 34 H92 M6 32 Q14 24 24 9 Q48 32 72 9 Q82 24 90 32 M22.5 31 V26 L24 23 L25.5 26 V31 M70.5 31 V26 L72 23 L73.5 26 V31",
        d2 = "M30 14 V34 M36 17.6 V34 M42 19.8 V34 M48 20.5 V34 M54 19.8 V34 M60 17.6 V34 M66 14 V34 M24 4 V1.5 M72 4 V1.5 M12 25 V34 M18 18 V34 M78 18 V34 M84 25 V34",
        d3 = "M22 15 H26 M70 15 H74 M22 19.5 H26 M70 19.5 H74 M23.2 9 L24 7 L24.8 9 M71.2 9 L72 7 L72.8 9",
    ),
    "fremont" to Art(
        0xFF8FB9A0, 0xFFB3262E,
        f = "M12 33 Q48 0 84 33 Q48 14 12 33 Z",
        s = "M4 32 H92 M4 37 H92 M30 32 V27 M39 32 V25 M48 32 V24 M57 32 V25 M66 32 V27",
        a = "M44 17 V10 l5 1.5 -5 1.5 M52 17 V10 l5 1.5 -5 1.5",
        d2 = "M21 32 V29 M34.5 32 V25.6 M43.5 32 V24.2 M52.5 32 V24.2 M61.5 32 V25.6 M75 32 V29 M8 34.5 H88",
        d3 = "M14 30 Q48 6 82 30 M30 34.5 V37 M48 34.5 V37 M66 34.5 V37",
    ),
    "broadway" to Art(
        0xFFC8402A,
        f = "M8 34 V29 Q26 16 46 29 V34 Z M50 34 V29 Q70 16 88 29 V34 Z",
        s = "M4 34 H92 M8 34 L17 25 L26 34 L35 25 L44 32 M52 32 L61 25 L70 34 L79 25 L88 34 M26 34 V44 M70 34 V44",
        d2 = "M12.5 34 V28 M21.5 34 V24.5 M30.5 34 V23.5 M39.5 34 V26 M56.5 34 V26 M65.5 34 V23.5 M74.5 34 V24.5 M83.5 34 V28 M48 30 V36",
        d3 = "M10 31.5 H44 M52 31.5 H86 M17 25.5 V34 M35 25.5 V34 M61 25.5 V34 M79 25.5 V34 M24 34 V36.5 M72 34 V36.5",
    ),
    "steel" to Art(
        0xFF3A3F44,
        f = "M20 34 V8 H28 V34 Z M68 34 V8 H76 V34 Z M28 14 H68 V21 H28 Z M44 14 V9 H52 V14 Z",
        s = "M4 30 H92 M4 37 H92 M20 12 L28 20 M28 12 L20 20 M68 12 L76 20 M76 12 L68 20",
        d2 = "M28 14 L34 21 L40 14 L46 21 L52 14 L58 21 L64 14 L68 21 M28 14 L24 9 M68 14 L72 9 M8 39.5 H60",
        d3 = "M20 25.5 H28 M68 25.5 H76 M20 30 H28 M68 30 H76 M28 17.5 H68 M45.5 11.5 H50.5 M22 8 V14 M74 8 V14 M10 30 V37 M16 30 V37 M80 30 V37 M86 30 V37",
    ),
    "burnside" to Art(
        0xFFA8977A,
        f = "M32 34 V19 H42 V34 Z M54 34 V19 H64 V34 Z M33 19 L37 13 L41 19 Z M55 19 L59 13 L63 19 Z",
        s = "M4 34 H92 M4 37.5 H92 M42 44 Q48 37 54 44 M10 34 L16 30 L22 34 L28 30 M68 30 L74 34 L80 30 L86 34",
        d2 = "M35 23 H39 M57 23 H61 M35 27 H39 M57 27 H61 M8 34 V31.5 M14 34 V31.5 M20 34 V31.5 M76 34 V31.5 M82 34 V31.5 M88 34 V31.5 M48 31.5 V37.5",
        d3 = "M32 23 V34 M42 23 V34 M54 23 V34 M64 23 V34 M37 13 V11 M59 13 V11",
    ),
    "morrison" to Art(
        0xFF6B7A86, 0xFFF2B705,
        f = "M34 44 V24 H42 V44 Z M54 44 V24 H62 V44 Z",
        s = "M4 34 H92 M36 24 V16 M40 24 V16 M56 24 V16 M60 24 V16 M6 34 L12 39 L18 34 L24 39 L30 34 M66 34 L72 39 L78 34 L84 39 L90 34",
        a = "M36 15 h0.1 M40 15 h0.1 M56 15 h0.1 M60 15 h0.1",
        d2 = "M34.5 16 H41.5 M54.5 16 H61.5 M36 40 H40 M56 40 H60 M48 31 V37",
        d3 = "M34 30 H42 M54 30 H62 M38 24 V44 M58 24 V44",
    ),
    "hawthorne" to Art(
        0xFF2F7A4F, 0xFFB3262E,
        f = "M28 34 V12 L32 8 H36 V34 Z M60 34 V8 H64 L68 12 V34 Z M36 22 H60 V28 H36 Z M45 22 V18 H51 V22 Z",
        s = "M4 34 H92 M4 34 Q16 24 28 28 M68 28 Q80 24 92 34",
        a = "M28 12 L32 8 H36 M60 8 H64 L68 12 M36 22 H60",
        d2 = "M28 16 L36 22 M36 16 L28 22 M60 16 L68 22 M68 16 L60 22 M10 33 L13 29.5 M17 31 L19 27.5 M79 27.5 L81 31 M85 29.5 L88 33 M36 28 V34 M60 28 V34 M42 28 V34 M54 28 V34",
        d3 = "M28 18.5 H36 M60 18.5 H68 M28 24 H36 M60 24 H68 M28 29.5 H36 M60 29.5 H68 M38 25 H58 M47 19.5 H49 M6 32 L10 34 M90 32 L86 34",
    ),
    "marquam" to Art(
        0xFF8A9097,
        f = "M4 30 H92 V36 H4 Z",
        s = "M10 22 H86 M4 30 L10 22 L16 30 L22 22 L28 30 L34 22 L40 30 L46 22 L52 30 L58 22 L64 30 L70 22 L76 30 L82 22 L88 30 M24 36 V44 M48 36 V44 M72 36 V44",
        d2 = "M80 36 L88 40 M4 33 H92",
    ),
    "tilikum" to Art(
        0xFFB9C7CE,
        f = "M30 34 V12 L32 5 L34 12 V34 Z M62 34 V12 L64 5 L66 12 V34 Z",
        s = "M4 34 H92 M32 9 L10 34 M32 13 L20 34 M32 9 L54 34 M32 13 L44 34 M64 9 L42 34 M64 13 L52 34 M64 9 L86 34 M64 13 L76 34",
        d2 = "M32 11 L15 34 M32 11 L49 34 M64 11 L47 34 M64 11 L81 34 M4 36 H92",
        d3 = "M32 15 L26 34 M32 15 L38 34 M64 15 L58 34 M64 15 L70 34",
    ),
    "ross-island" to Art(
        0xFF1E5AA8,
        f = "M12 34 H84 V44 Q48 30 12 44 Z",
        s = "M4 34 H92 M24 34 V40 M36 34 V37.5 M60 34 V37.5 M72 34 V40",
        d2 = "M18 34 L22 40 M28 34 L32 38 M40 34 L44 36.5 M52 36.5 L56 34 M64 38 L68 34 M74 40 L78 34",
        d3 = "M14 36 H82 M48 34 V36.8",
    ),
    "sellwood" to Art(
        0xFF8B5A3C,
        f = "M4 32 H92 V35 H4 Z",
        s = "M6 44 Q19 30 32 44 M32 44 Q48 26 64 44 M64 44 Q77 30 90 44 M19 35 V37 M77 35 V37 M10 32 V29 M22 32 V29 M34 32 V29 M46 32 V29 M58 32 V29 M70 32 V29 M82 32 V29",
        d2 = "M12 35 V38.5 M26 35 V38 M40 35 V36.5 M56 35 V36.5 M70 35 V38 M84 35 V38.5",
        d3 = "M4 33.5 H92",
    ),
    "interstate-5" to Art(
        0xFF6E8597,
        f = "M30 34 V8 H36 V34 Z M60 34 V8 H66 V34 Z",
        s = "M4 34 H92 M36 11 H60 M36 16 H60 M4 34 L10 27 H24 L30 25 M66 25 L72 27 H86 L92 34 M4 40 Q48 29 92 40",
        d2 = "M10 27 V34 M17 27 V34 M24 26 V34 M72 26 V34 M79 27 V34 M86 27 V34 M36 21 H60 M30 12 L36 20 M36 12 L30 20 M60 12 L66 20 M66 12 L60 20",
        d3 = "M30 24 H36 M60 24 H66 M30 30 H36 M60 30 H66",
    ),
    "interstate-205" to Art(
        0xFFA3A39E,
        f = "M4 30 H92 V33 L70 38 L48 33 L26 38 L4 33 Z",
        s = "M4 30 H92 M26 38 V45 M70 38 V45",
        d2 = "M4 31.5 H92 M15 30 V33.8 M37 30 V34.6 M59 30 V34.6 M81 30 V33.8",
    ),
    "blumenauer" to Art(
        0xFF1F5A3D,
        f = "M8 33 H88 V35.5 H8 Z",
        s = "M16 34 Q48 10 80 34 M28 34 V27 M38 34 V23.5 M48 34 V22 M58 34 V23.5 M68 34 V27",
        d2 = "M22 34 V30 M33 34 V25 M43 34 V22.6 M53 34 V22.6 M63 34 V25 M74 34 V30",
        d3 = "M8 36 H88",
        road = true,
    ),
)

/** How a bridge icon is inked. */
enum class BridgeInk {
    /** Full color: the bridge sheet, a stamped passport cell, the ride banner. */
    FULL,
    /** Outline only, muted: a bridge not crossed yet. */
    GHOST,
}

private fun parse(d: String): Path = if (d.isEmpty()) Path() else PathParser().parsePathString(d).toPath()

/**
 * One bridge, drawn from its paths. Twice as wide as tall. Detail lines appear when detail is 2 or 3
 * (64 dp and wider, 80 dp and wider on the design canvas).
 */
@Composable
fun BridgeIcon(id: String, modifier: Modifier = Modifier, ink: BridgeInk = BridgeInk.FULL, detail: Int = 1, label: String? = null) {
    val art = ART[id] ?: return
    val look = LocalLook.current
    val paths = remember(id) { listOf(art.f, art.s, art.a, art.d2, art.d3, if (art.road) ROAD else WAVES).map(::parse) }
    val ghost = ink == BridgeInk.GHOST
    // E: the bridge's own paint with an ink outline. C: white line art. Never the route color.
    val stroke = when {
        look.dark -> if (ghost) Color(0xFF5A5A60) else PdxC.White
        else -> if (ghost) Color(0xFF7A86A3) else PdxE.Ink
    }
    val fill = if (look.dark || ghost) Color.Transparent else Color(art.color)
    val accent = if (look.dark || ghost) stroke else Color(art.trim ?: 0xFF9B1B30)
    val water = when {
        look.dark -> if (ghost) PdxC.Line else Color(0xFF3A3A40)
        else -> if (ghost) Color(0xFFB9DADC) else Color(0xFF5FB3B8)
    }
    Canvas(modifier.aspectRatio(2f).then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)) {
        val k = size.width / 96f
        scale(k, k, pivot = Offset.Zero) {
            val sw = 3f
            fun line(w: Float, dashed: Boolean = false) = Stroke(
                w, cap = if (look.dark) StrokeCap.Butt else StrokeCap.Round, join = StrokeJoin.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 5f)) else null,
            )
            drawPath(paths[5], water, style = line(sw * 0.6f, dashed = art.road))
            if (fill != Color.Transparent) drawPath(paths[0], fill)
            drawPath(paths[0], stroke, style = line(sw))
            drawPath(paths[1], stroke, style = line(sw))
            if (detail >= 2) drawPath(paths[3], stroke, style = line(sw * 0.38f))
            if (detail >= 3) drawPath(paths[4], stroke, style = line(sw * 0.24f))
            if (art.a.isNotEmpty()) drawPath(paths[2], accent, style = line(sw + 0.6f))
        }
    }
}
