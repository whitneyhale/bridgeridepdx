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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LookPrefs.load(this)
        setContent { BridgeRideTheme { AppScreen() } }
    }
}

private const val STALE_MS = 5_000L // N-09: marker turns orange after 5 s without a fix

private enum class Tab(val label: String) { MAP("Map"), LIBRARY("Library"), SETTINGS("Settings") }

/** Nav: Map, Library, Settings (decided 2026-11-05). */
@Composable
fun AppScreen() {
    var tab by remember { mutableStateOf(Tab.MAP) }
    Scaffold(
        bottomBar = {
            val look = LocalLook.current
            Column {
            HorizontalDivider(thickness = look.border, color = look.borderColor)
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
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
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                Tab.MAP -> MapTab()
                Tab.LIBRARY -> LibraryTab()
                Tab.SETTINGS -> SettingsTab()
            }
        }
    }
}

@Composable
private fun MapTab() {
    val context = LocalContext.current
    val status by Recorder.status.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val handle = remember { MapHandle() }

    // Keep the screen on while recording (S1).
    LocalView.current.keepScreenOn = status.recording
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        message = when {
            fine -> { Recorder.start(context); null }
            coarse -> "Approximate location only. Recording needs precise location: tap Start again and choose Precise."
            else -> "Location is off for Bridge Ride. Recording needs it while a ride runs."
        }
    }
    fun hasFine() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun startRide() {
        if (hasFine()) { Recorder.start(context); message = null; return }
        val asks = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) asks += Manifest.permission.POST_NOTIFICATIONS
        permissions.launch(asks.toTypedArray())
    }
    fun locate() {
        status.lastFix?.let { centerOn(handle, it.lat, it.lon); return }
        if (!hasFine()) { message = "Tap Start ride to allow location, then Locate works."; return }
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { loc -> if (loc != null) centerOn(handle, loc.latitude, loc.longitude) else message = "No location yet." }
        } catch (e: SecurityException) { message = "Location is off for Bridge Ride." }
    }

    Box(Modifier.fillMaxSize()) {
        BridgeMap(Modifier.fillMaxSize(), handle)
        Column(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GpsLine(status, now)
        }
        SmallButton("Locate", Modifier.align(Alignment.CenterEnd).padding(12.dp), ::locate)
        Panel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stats(status, now)
                if (status.recording) HoldToStop { Recorder.stop(context) }
                else BigButton("Start ride", onClick = ::startRide)
                message?.let { Warning(it) }
            }
        }
    }
}

@Composable
private fun LibraryTab() {
    val context = LocalContext.current
    val status by Recorder.status.collectAsStateWithLifecycle()
    var rides by remember { mutableStateOf(emptyList<RideSummary>()) }
    var pendingExport by remember { mutableStateOf<RideSummary?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(status.recording) { rides = withContext(Dispatchers.IO) { RideStore.list(context) } }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        val ride = pendingExport ?: return@rememberLauncherForActivityResult
        pendingExport = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(RideStore.gpx(ride).toByteArray()) }
            withContext(Dispatchers.Main) { message = "GPX saved." }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)); Text("Ride log", style = MaterialTheme.typography.headlineMedium) }
        message?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.secondary) } }
        if (rides.isEmpty()) item { Text("No rides yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(rides, key = { it.file.name }) { ride ->
            RideRow(ride, recording = status.recording && ride.startMs == status.startMs) {
                pendingExport = ride
                exporter.launch(ride.file.name.replace(".csv", ".gpx"))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsTab() {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        val choice by LookPrefs.choice.collectAsStateWithLifecycle()
        Text("Look", fontWeight = FontWeight.Bold)
        LookPicker(choice) { LookPrefs.set(context, it) }
        BatteryCheck()
        Text("Bridge Ride $version · test build", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Map © OpenFreeMap, OpenMapTiles, OpenStreetMap contributors. Map style based on OSM Liberty (CC BY 4.0). " +
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

@Composable
private fun GpsLine(s: RecorderStatus, now: Long) {
    val (text, warn) = when {
        !s.recording -> "GPS off until you start" to false
        s.lastFix == null -> "Waiting for GPS" to true
        now - s.lastFix.timeMs > STALE_MS -> "GPS estimated · last fix ${(now - s.lastFix.timeMs) / 1000} s ago" to true
        else -> "GPS good · ±${s.lastFix.accuracyM.toInt()} m" to false
    }
    val bg = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val fg = if (warn) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondary
    Row(
        Modifier.fillMaxWidth().clip(lookShape()).background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (warn) Icon(painterResource(R.drawable.ic_warning), contentDescription = "Warning", tint = fg, modifier = Modifier.size(22.dp))
        Text(text, color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Warning text always carries the warning icon, so it never depends on color alone (palette fix, 2026-11-09). */
@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = "Warning", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Stats(s: RecorderStatus, now: Long) {
    val elapsed = s.startMs?.let { (if (s.recording) now else s.lastFix?.timeMs ?: it) - it } ?: 0L
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Time", "%d:%02d".format(elapsed / 60_000, (elapsed / 1000) % 60))
        Stat("Miles", "%.2f".format(s.distanceM / 1609.344))
        Stat("Gaps >10 s", s.gapsOver10s.toString())
        Stat("Longest gap", "${s.longestGapMs / 1000} s")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = LocalLook.current.numbers)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

/** End needs a 1 s press so a bump does not end the ride (workflow review, 2026-11-05). */
@Composable
private fun HoldToStop(onStop: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(lookShape())
            .background(MaterialTheme.colorScheme.onBackground)
            .semantics { contentDescription = "Hold for one second to stop the ride" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    job = scope.launch {
                        val t0 = System.currentTimeMillis()
                        while (progress < 1f) { progress = (System.currentTimeMillis() - t0) / 1000f; delay(16) }
                        onStop()
                    }
                    waitForUpOrCancellation()
                    job?.cancel(); progress = 0f
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(64.dp).background(MaterialTheme.colorScheme.primary))
        Text(
            "Hold to stop", Modifier.fillMaxWidth().padding(vertical = 18.dp),
            color = MaterialTheme.colorScheme.background, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun RideRow(ride: RideSummary, recording: Boolean, onExport: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ride.name, fontWeight = FontWeight.SemiBold)
                val gaps = if (ride.gaps.gapsOver10s == 0) "no gaps over 10 s" else "${ride.gaps.gapsOver10s} gaps over 10 s, longest ${ride.gaps.longestGapMs / 1000} s"
                Text("%.2f mi · %d fixes · %s".format(ride.distanceM / 1609.344, ride.fixes, gaps),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            if (!recording) SmallButton("GPX", onClick = onExport)
        }
    }
}
