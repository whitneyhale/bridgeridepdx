package pdx.bridgeride.app

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import pdx.bridgeride.core.BikeAccess
import pdx.bridgeride.core.BridgeEnd
import pdx.bridgeride.core.Geo
import pdx.bridgeride.core.LatLon
import pdx.bridgeride.core.Units
import kotlin.math.hypot
import kotlin.math.roundToInt

// Map styles are app assets (style_e.json light, style_c.json dark), built by tools/build_map_styles.py.
// Tiles: OpenFreeMap, free vector tiles, no key (openfreemap.org).
private val PORTLAND = LatLng(45.515, -122.670)
private const val RIDE_ZOOM = 16.5

/** Where and how the bike marker is drawn (task 1.4). */
data class MarkerState(
    val at: LatLon,
    /** Compass direction of the pointer, or null to hide it until the rider has moved. */
    val headingDeg: Double?,
    /** True when the position is a guess (GPS gap or weak signal): drawn in the warning color. */
    val estimated: Boolean,
)

/**
 * Everything the screens ask of the map: the line to follow, the bike marker, the camera.
 * It remembers what it was asked to show, so a style change (light to dark) redraws it all.
 * The line and the bridge dots are map layers; the bike marker is drawn over the map (BikeMarker).
 */
class MapController {
    var map: MapLibreMap? = null
        private set
    private var style: Style? = null
    private var ends: List<BridgeEnd> = emptyList()
    private var density = 1f

    private var route: List<LatLon>? = null
    private var target: MarkerState? = null
    private var drawn: MarkerState? = null
    private var animator: ValueAnimator? = null
    private var lastFrameMs = 0L

    /** True while the camera rides along with the marker. A pan or pinch by the rider turns it off. */
    var following = false
        private set
    private var cameraBearing: Double? = null
    private var cameraHoldUntilMs = 0L
    /** follow() was asked before the first fix: zoom in as soon as there is a marker to zoom to. */
    private var zoomWhenMarked = false
    private var padTopPx = 0
    private var padBottomPx = 0

    var onUserMove: () -> Unit = {}
    var onBridgeTap: (String) -> Unit = {}

    /** The marker as the screen should draw it, or null when there is none. Compose state: reading it redraws. */
    var onScreen by mutableStateOf<MarkerOnScreen?>(null)
        private set

    fun attach(map: MapLibreMap, ends: List<BridgeEnd>, density: Float) {
        this.map = map
        this.ends = ends
        this.density = density
        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && following) {
                following = false
                onUserMove()
            }
        }
        map.addOnCameraMoveListener { pushMarker() } // the map moved under the marker: put it back on its spot
        map.addOnMapClickListener { tap ->
            // The nearest bridge end within a thumb's width (28 dp) of the tap.
            val p = map.projection.toScreenLocation(tap)
            val near = this.ends.minByOrNull { e ->
                val q = map.projection.toScreenLocation(LatLng(e.lat, e.lon))
                hypot((q.x - p.x).toDouble(), (q.y - p.y).toDouble())
            }
            val hit = near?.takeIf { e ->
                val q = map.projection.toScreenLocation(LatLng(e.lat, e.lon))
                hypot((q.x - p.x).toDouble(), (q.y - p.y).toDouble()) <= 28 * density
            }
            if (hit != null) { onBridgeTap(hit.id); true } else false
        }
    }

    fun detach() {
        animator?.cancel(); animator = null
        map = null; style = null
        onScreen = null
    }

    /** Called when a style has loaded: add our layers on top of it and redraw what was showing. */
    fun onStyle(style: Style, look: Look) {
        this.style = style
        style.addSource(GeoJsonSource("route"))
        style.addLayer(
            LineLayer("route-casing", "route").withProperties(
                lineColor(look.routeCasing), lineWidth(11f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer("route-line", "route").withProperties(
                lineColor(look.route), lineWidth(6.5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        addBridgeEnds(style, ends, look)
        pushRoute()
        pushMarker()
    }

    fun setRoute(points: List<LatLon>?) {
        route = points
        pushRoute()
    }

    private fun pushRoute() {
        val source = style?.getSourceAs<GeoJsonSource>("route") ?: return
        val pts = route
        if (pts == null || pts.size < 2) source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        else source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(pts.map { Point.fromLngLat(it.lon, it.lat) })))
    }

    /**
     * Where the marker is on the screen right now. The marker is drawn by Compose on top of the map
     * (BikeMarker), not as a map layer: that keeps it sharp at any size, lets it carry a spoken label,
     * and lets the emulator tests see it. Refreshed on every marker step and every camera move.
     */
    private fun pushMarker() {
        val map = map
        val m = drawn
        if (map == null || m == null) { onScreen = null; return }
        val p = map.projection.toScreenLocation(LatLng(m.at.lat, m.at.lon))
        onScreen = MarkerOnScreen(p.x, p.y, m.headingDeg?.let { Geo.norm(it - map.cameraPosition.bearing) }, m.headingDeg, m.estimated)
    }

    /**
     * Moves the marker. It glides from where it is drawn to the new spot over about a second, so the
     * one-fix-a-second GPS does not make it hop; the camera glides with it while following.
     * bearingDeg is which way is up on the map while following (the direction of the line).
     */
    fun setMarker(next: MarkerState?, bearingDeg: Double? = null) {
        if (bearingDeg != null) cameraBearing = bearingDeg
        target = next
        val from = drawn
        animator?.cancel()
        if (next != null && zoomWhenMarked) {
            drawn = next
            pushMarker()
            follow()
            return
        }
        if (next == null || from == null || Geo.distanceM(from.at, next.at) > 150.0 || Geo.distanceM(from.at, next.at) < 0.3) {
            drawn = next
            pushMarker()
            moveCameraToMarker()
            return
        }
        val startBearing = map?.cameraPosition?.bearing
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 950
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val nowMs = SystemClock.uptimeMillis()
                val t = a.animatedFraction.toDouble()
                if (t < 1.0 && nowMs - lastFrameMs < 33) return@addUpdateListener // about 30 frames a second is plenty
                lastFrameMs = nowMs
                val heading = when {
                    next.headingDeg == null -> null
                    from.headingDeg == null -> next.headingDeg
                    else -> Geo.norm(from.headingDeg + Geo.turnDeg(from.headingDeg, next.headingDeg) * t)
                }
                drawn = MarkerState(
                    LatLon(from.at.lat + (next.at.lat - from.at.lat) * t, from.at.lon + (next.at.lon - from.at.lon) * t),
                    heading, next.estimated,
                )
                pushMarker()
                moveCameraToMarker(startBearing, t)
            }
            start()
        }
    }

    private fun moveCameraToMarker(startBearing: Double? = null, t: Double = 1.0) {
        val map = map ?: return
        val at = drawn?.at ?: return
        if (!following || SystemClock.uptimeMillis() < cameraHoldUntilMs) return
        val wanted = cameraBearing ?: map.cameraPosition.bearing
        val bearing = if (startBearing == null) wanted else Geo.norm(startBearing + Geo.turnDeg(startBearing, wanted) * t)
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(at.lat, at.lon)).zoom(map.cameraPosition.zoom).bearing(bearing).tilt(0.0)
                    .padding(0.0, padTopPx.toDouble(), 0.0, padBottomPx.toDouble()).build(),
            ),
        )
    }

    /** Space taken by the banner and the bottom card, so the marker sits in the clear part of the map. */
    fun setPadding(topPx: Int, bottomPx: Int) {
        padTopPx = topPx
        padBottomPx = bottomPx
    }

    /** Start riding along with the marker: zoom in on it, line direction up. */
    fun follow() {
        following = true
        val map = map
        val at = (drawn ?: target)?.at
        zoomWhenMarked = map == null || at == null
        if (map == null || at == null) return
        cameraHoldUntilMs = SystemClock.uptimeMillis() + 750
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(at.lat, at.lon)).zoom(RIDE_ZOOM).bearing(cameraBearing ?: 0.0).tilt(0.0)
                    .padding(0.0, padTopPx.toDouble(), 0.0, padBottomPx.toDouble()).build(),
            ),
            700,
        )
    }

    /** Stop following and put north back up (after a ride ends). */
    fun release() {
        following = false
        zoomWhenMarked = false
        cameraBearing = null
        val map = map ?: return
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(map.cameraPosition).bearing(0.0).padding(0.0, 0.0, 0.0, 0.0).build(),
            ),
            400,
        )
    }

    /** Show a whole line, north up, clear of the panels at the top and bottom. */
    fun fit(points: List<LatLon>, topPx: Int, bottomPx: Int) {
        val map = map ?: return
        if (points.size < 2) return
        following = false
        zoomWhenMarked = false
        val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lon) }).build()
        val side = (32 * density).toInt()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0.0, 0.0, side, topPx + side, side, bottomPx + side), 600)
    }

    fun centerOn(lat: Double, lon: Double) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0), 600)
    }
}

/**
 * The map with every bridge end (task 1.2), the line being followed (1.6) and the bike marker (1.4).
 * Bikeable ends are dots in the look's bridge color (never the route color); ends of bridges closed
 * to bikes (Fremont, Marquam) are gray. The style follows the look and is swapped when the rider changes it.
 */
@Composable
fun BridgeMap(modifier: Modifier, controller: MapController, ends: List<BridgeEnd>) {
    val context = LocalContext.current
    val look = LocalLook.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.detach()
            mapView.onDestroy()
        }
    }

    Box(modifier.clipToBounds()) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    map.cameraPosition = CameraPosition.Builder().target(PORTLAND).zoom(11.5).build()
                    controller.attach(map, ends, context.resources.displayMetrics.density)
                }
                mapView
            },
            modifier = Modifier.fillMaxSize(),
        )
        controller.onScreen?.let { BikeMarker(it) }
    }

    // Load the style for the current look; runs again when the rider switches light and dark.
    DisposableEffect(look.mapStyle) {
        mapView.contentDescription = "Map loading"
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(look.mapStyle)) { style ->
                controller.onStyle(style, look)
                // MapLibre sets its own description on the view; replace it once the ends are drawn,
                // so screen readers and Maestro know the bridges are on the map and which look is showing.
                mapView.contentDescription = "Map, ${ends.size} bridge ends, ${if (look.dark) "dark" else "light"}"
            }
        }
        onDispose { }
    }
}

private fun addBridgeEnds(style: Style, ends: List<BridgeEnd>, look: Look) {
    fun layer(id: String, list: List<BridgeEnd>, color: String) {
        val features = list.map { e ->
            Feature.fromGeometry(Point.fromLngLat(e.lon, e.lat)).apply {
                addStringProperty("id", e.id); addStringProperty("end", e.end)
            }
        }
        style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(features)))
        style.addLayer(
            CircleLayer(id, id).withProperties(
                circleRadius(7f), circleColor(color), circleStrokeColor(look.bridgeDotRing), circleStrokeWidth(2.5f),
            ),
        )
    }
    layer("bridge-ends-closed", ends.filter { it.access == BikeAccess.NO }, look.bridgeDotClosed)
    layer("bridge-ends", ends.filter { it.access != BikeAccess.NO }, look.bridgeDot)
}

/** The marker in screen pixels. pointerDeg is where the pointer sits on the screen (0 is up), already turned for a rotated map. */
data class MarkerOnScreen(val x: Float, val y: Float, val pointerDeg: Double?, val headingDeg: Double?, val estimated: Boolean)

private const val BIKE_FRAME = "M-13 8 H0 L-5 -6 Z M-5 -6 H8 M0 8 L8.6 -4.5 M8 -6 L13 8 M8.4 -5 L7 -13 M2.5 -13 H7 C10.5 -13 13.2 -12 13.8 -9 M-5 -6 V-9.6 M-9.6 -9.8 H-1.4"
private const val POINTER = "M0 -32 L8 -21 H-8 Z"

/**
 * The bike marker (task 1.4): a round puck with a bicycle in it, always upright, and a pointer that
 * circles it to show which way the rider is heading. It is a bicycle, never a dot or an arrow
 * (decided 2026-11-05). While the position is a guess it is drawn in the warning color, and says so
 * in its label, so the state never depends on color alone. Shapes are from the E and C design boards.
 */
@Composable
private fun BikeMarker(m: MarkerOnScreen) {
    val look = LocalLook.current
    val frame = remember { PathParser().parsePathString(BIKE_FRAME).toPath() }
    val pointer = remember { PathParser().parsePathString(POINTER).toPath() }
    val box = 100.dp
    val half = with(LocalDensity.current) { (box / 2).toPx() }
    val fill = Color(if (m.estimated) look.markerEstFill else look.markerFill)
    val ring = Color(if (m.estimated) look.markerEstRing else look.markerRing)
    val bike = Color(if (m.estimated) look.markerEstBike else look.markerBike)
    // Estimated: the warning color (E's hazard orange is the puck fill, C's signal red is the puck ring).
    val pointerColor = Color(if (!m.estimated) look.markerRing else if (look.dark) look.markerEstRing else look.markerEstFill)
    val words = "Bike marker, " + (if (m.estimated) "position estimated" else "position good") +
        (m.headingDeg?.let { ", heading ${Units.compass(it)}" } ?: ", heading not known yet")
    Canvas(
        Modifier.offset { IntOffset((m.x - half).roundToInt(), (m.y - half).roundToInt()) }.size(box)
            .semantics { contentDescription = words },
    ) {
        // The design draws the puck 40 units wide (circle radius 17 plus its outline); here that is 54 dp.
        val k = 54.dp.toPx() / 40f
        translate(size.width / 2, size.height / 2) {
            scale(k, k, pivot = Offset.Zero) {
                m.pointerDeg?.let { deg ->
                    rotate(deg.toFloat(), pivot = Offset.Zero) {
                        drawPath(pointer, pointerColor)
                        drawPath(pointer, Color(look.markerPointerEdge), style = Stroke(1.6f, join = StrokeJoin.Round))
                    }
                }
                drawCircle(fill, 17f, Offset.Zero)
                drawCircle(ring, 17f, Offset.Zero, style = Stroke(3f))
                scale(0.55f, 0.55f, pivot = Offset.Zero) {
                    val line = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawCircle(bike, 9.4f, Offset(-13f, 8f), style = Stroke(2.8f))
                    drawCircle(bike, 9.4f, Offset(13f, 8f), style = Stroke(2.8f))
                    drawPath(frame, bike, style = line)
                    drawCircle(bike, 2.4f, Offset(0f, 8f), style = Stroke(2f))
                }
            }
        }
    }
}
