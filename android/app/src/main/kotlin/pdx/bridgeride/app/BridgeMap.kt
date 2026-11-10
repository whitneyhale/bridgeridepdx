package pdx.bridgeride.app

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import pdx.bridgeride.core.BikeAccess
import pdx.bridgeride.core.BridgeEnd
import pdx.bridgeride.core.BridgeEnds

// Map styles are app assets (style_e.json light, style_c.json dark), built by tools/build_map_styles.py.
// Tiles: OpenFreeMap, free vector tiles, no key (openfreemap.org).
private val PORTLAND = LatLng(45.515, -122.670)

fun loadBridgeEnds(context: Context): List<BridgeEnd> =
    context.assets.open("bridge_ends.csv").bufferedReader().use { BridgeEnds.parse(it.readLines()) }

/** Holds the map so the locate button can move the camera. */
class MapHandle { var map: MapLibreMap? = null }

/**
 * Task 1.2: the map with every bridge end. Bikeable ends are dots in the look's bridge color
 * (never the route color); ends of bridges closed to bikes (Fremont, Marquam) are gray.
 * The style follows the look and is swapped when the rider changes it. The bike marker comes in task 1.4.
 */
@Composable
fun BridgeMap(modifier: Modifier, handle: MapHandle) {
    val context = LocalContext.current
    val ends = remember { loadBridgeEnds(context) }
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
            handle.map = null
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = {
            mapView.getMapAsync { map ->
                handle.map = map
                map.cameraPosition = CameraPosition.Builder().target(PORTLAND).zoom(11.5).build()
            }
            mapView
        },
        modifier = modifier,
    )

    // Load the style for the current look; runs again when the rider switches light and dark.
    DisposableEffect(look.mapStyle) {
        mapView.contentDescription = "Map loading"
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(look.mapStyle)) { style ->
                addBridgeEnds(style, ends, look)
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

@SuppressLint("MissingPermission") // only called after the precise-location check
fun centerOn(handle: MapHandle, lat: Double, lon: Double) {
    handle.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0), 600)
}
