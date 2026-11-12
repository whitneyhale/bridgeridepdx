package pdx.bridgeride.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pdx.bridgeride.core.RidePlan
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LookPrefs.load(this)
        setContent { BridgeRideTheme { AppScreen() } }
    }
}

enum class Tab(val label: String) { MAP("Map"), LIBRARY("Library"), SETTINGS("Settings") }

/** Where the rider is in the app. Kept above the tabs so Library can send a ride to the Map tab to be ridden again. */
class AppState {
    var tab by mutableStateOf(Tab.MAP)
    /** The Plan sheet is open. */
    var planning by mutableStateOf(false)
    /** The ride picked in Plan, shown on the map before Go. */
    var plan by mutableStateOf<RidePlan?>(null)
    /** A finished ride whose summary is showing. */
    var summary by mutableStateOf<File?>(null)
    /** A bridge whose details are showing. */
    var bridge by mutableStateOf<String?>(null)
    /** Set when End is held, so the summary of that ride opens once the recorder has stopped. */
    var ending: Long? = null
}

/** Nav: Map, Library, Settings (decided 2026-11-05). Hidden during a ride and on the summary: nothing to mis-tap. */
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val app = remember { AppState() }
    val status by Recorder.status.collectAsStateWithLifecycle()
    val data by produceState<AppData?>(null) { value = withContext(Dispatchers.Default) { AppData.get(context) } }
    var reload by remember { mutableIntStateOf(0) }
    val loaded = data
    val rides by produceState(emptyList<RideSummary>(), loaded, status.recording, reload) {
        if (loaded != null) value = withContext(Dispatchers.IO) { RideStore.list(context, loaded.ends, status.startMs.takeIf { status.recording }) }
    }

    LaunchedEffect(status.recording) {
        if (status.recording) { app.tab = Tab.MAP; app.planning = false; app.bridge = null }
        else app.ending?.let { start -> app.summary = RideStore.newRideFile(context, start); app.ending = null; app.plan = null }
    }

    if (loaded == null) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)); return }
    val fullScreen = status.recording || app.summary != null
    Scaffold(
        bottomBar = { if (!fullScreen) TabBar(app) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (app.tab) {
                Tab.MAP -> MapTab(app, loaded, rides, status)
                Tab.LIBRARY -> LibraryTab(app, loaded, rides, status.startMs.takeIf { status.recording })
                Tab.SETTINGS -> SettingsTab()
            }
            val bridge = app.bridge?.let { loaded.infoById[it] }
            if (bridge != null && !status.recording) BridgeSheet(bridge, stamps(rides)[bridge.id] ?: 0) { app.bridge = null }
            app.summary?.let { file -> SummaryScreen(file, loaded, rides) { app.summary = null; reload++ } }
        }
    }
}

@Composable
private fun TabBar(app: AppState) {
    val look = LocalLook.current
    Column {
        HorizontalDivider(thickness = look.border, color = look.borderColor)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Tab.entries.forEach { t ->
                NavigationBarItem(
                    selected = app.tab == t,
                    onClick = { app.tab = t; app.bridge = null },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    icon = {
                        Icon(
                            painterResource(when (t) { Tab.MAP -> R.drawable.ic_tab_map; Tab.LIBRARY -> R.drawable.ic_tab_library; Tab.SETTINGS -> R.drawable.ic_tab_settings }),
                            contentDescription = null,
                        )
                    },
                    label = { Text(t.label, fontWeight = FontWeight.Bold) },
                )
            }
        }
    }
}

@Composable
private fun MapTab(app: AppState, data: AppData, rides: List<RideSummary>, status: RecorderStatus) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val follow by Nav.state.collectAsStateWithLifecycle()
    val ridingPlan by Nav.plan.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<RidePlan?>(null) }
    var sheetPx by remember { mutableIntStateOf(0) }
    val controller = remember { MapController() }

    // Keep the screen on while recording (S1).
    LocalView.current.keepScreenOn = status.recording

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        message = when {
            fine -> { Recorder.start(context, pending); null }
            coarse -> "Approximate location only. A ride needs precise location: try again and choose Precise."
            else -> "Location is off for Bridge Ride. A ride needs it while it runs."
        }
    }
    fun hasFine() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun startRide(plan: RidePlan?) {
        if (hasFine()) { Recorder.start(context, plan); message = null; return }
        pending = plan
        val asks = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) asks += Manifest.permission.POST_NOTIFICATIONS
        permissions.launch(asks.toTypedArray())
    }
    fun locate() {
        if (!hasFine()) { message = "Start a ride once to allow location, then Locate works."; return }
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc -> if (loc != null) controller.centerOn(loc.latitude, loc.longitude) else message = "No location yet." }
        } catch (e: SecurityException) { message = "Location is off for Bridge Ride." }
    }

    DisposableEffect(controller) {
        controller.onBridgeTap = { id -> if (!Recorder.status.value.recording && !app.planning) app.bridge = id }
        onDispose { controller.onBridgeTap = {} }
    }
    // What the map shows besides the bridges: the line being ridden, or the one picked in Plan.
    val shownPlan = when { status.recording -> ridingPlan; app.planning -> app.plan; else -> null }
    LaunchedEffect(shownPlan, status.recording, sheetPx) {
        controller.setRoute(shownPlan?.line?.points)
        if (!status.recording) {
            controller.setMarker(null)
            if (shownPlan != null) controller.fit(shownPlan.line.points, with(density) { 16.dp.roundToPx() }, sheetPx)
            else controller.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        BridgeMap(Modifier.fillMaxSize(), controller, data.ends)
        when {
            status.recording -> RideOverlay(status, follow, ridingPlan, controller) {
                app.ending = status.startMs
                Recorder.stop(context)
            }
            app.planning -> PlanSheet(app, data, rides, onHeight = { sheetPx = it }) { plan -> startRide(plan) }
            else -> {
                SmallButton("Locate", Modifier.align(Alignment.CenterEnd).padding(12.dp), ::locate)
                Panel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 8.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BigButton("Plan a ride") { app.bridge = null; app.planning = true }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallButton("Record only") { startRide(null) }
                            Text("No line to follow. Tap a bridge dot for its details.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                        message?.let { Warning(it) }
                    }
                }
            }
        }
        if (app.planning && message != null && !status.recording) {
            Panel(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp)) { Box(Modifier.padding(12.dp)) { Warning(message ?: "") } }
        }
    }
}

@Composable
private fun SettingsTab() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        val choice by LookPrefs.choice.collectAsStateWithLifecycle()
        Text("Look", fontWeight = FontWeight.Bold)
        LookPicker(choice) { LookPrefs.set(context, it) }
        BatteryCheck()
        NotBuilt("Home, Work and favorite places. Due in 0.7.")
        NotBuilt("Route style (greenway first, fewest turns). Due in 0.4.")
        Text("Bridge Ride $version · test build", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Map © OpenFreeMap, OpenMapTiles, OpenStreetMap contributors. Map style based on OSM Liberty (CC BY 4.0). " +
                "Built-in ride lines: BRouter on OpenStreetMap data (ODbL). Bridge and riverbank data: OpenStreetMap contributors. " +
                "Fonts: Bricolage Grotesque, DM Sans, Archivo, IBM Plex Mono (SIL Open Font License).",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
    }
}

@Composable
private fun BatteryCheck() {
    val context = LocalContext.current
    val pm = context.getSystemService(PowerManager::class.java)
    val ok = pm.isIgnoringBatteryOptimizations(context.packageName)
    if (ok) { Text("Battery: Unrestricted. Tracking will not be stopped by battery saving."); return }
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Warning("Battery may stop tracking")
            Text("Open App info, tap Battery, choose Unrestricted. Also keep Bridge Ride off the sleeping apps list (N-07).")
            SmallButton("Open App info") {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }
        }
    }
}
