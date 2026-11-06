package pdx.bridgeride.app

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** OpenFreeMap: free vector tiles, no key (openfreemap.org). */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private val PORTLAND = LatLng(45.515, -122.670)

fun loadBridgeEnds(context: Context): List<BridgeEnd> =
    context.assets.open("bridge_ends.csv").bufferedReader().use { BridgeEnds.parse(it.readLines()) }

/** Holds the map so the locate button can move the camera. */
class MapHandle { var map: MapLibreMap? = null }

/**
 * Task 1.2: the map with every bridge end. Bikeable ends are blue-green dots;
 * ends of bridges closed to bikes (Fremont, Marquam) are gray. The bike marker comes in task 1.4.
 */
@Composable
fun BridgeMap(modifier: Modifier, handle: MapHandle) {
    val context = LocalContext.current
    val ends = remember { loadBridgeEnds(context) }
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
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style -> addBridgeEnds(style, ends) }
            }
            mapView
        },
        modifier = modifier.semantics { contentDescription = "Map, ${ends.size} bridge ends" },
    )
}

private fun addBridgeEnds(style: Style, ends: List<BridgeEnd>) {
    fun layer(id: String, list: List<BridgeEnd>, color: String) {
        val features = list.map { e ->
            Feature.fromGeometry(Point.fromLngLat(e.lon, e.lat)).apply {
                addStringProperty("id", e.id); addStringProperty("end", e.end)
            }
        }
        style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(features)))
        style.addLayer(
            CircleLayer(id, id).withProperties(
                circleRadius(6f), circleColor(color), circleStrokeColor("#FFFFFF"), circleStrokeWidth(2f),
            ),
        )
    }
    layer("bridge-ends-closed", ends.filter { it.access == BikeAccess.NO }, "#8A9296")
    layer("bridge-ends", ends.filter { it.access != BikeAccess.NO }, "#367C99")
}

@SuppressLint("MissingPermission") // only called after the precise-location check
fun centerOn(handle: MapHandle, lat: Double, lon: Double) {
    handle.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), 15.0), 600)
}
