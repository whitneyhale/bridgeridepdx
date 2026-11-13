package pdx.bridgeride.app

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pdx.bridgeride.core.Banner
import pdx.bridgeride.core.BikeAccess
import pdx.bridgeride.core.BridgeInfo
import pdx.bridgeride.core.FollowState
import pdx.bridgeride.core.GpsState
import pdx.bridgeride.core.RidePlan
import pdx.bridgeride.core.Units
import java.io.File

private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/** How many times each bridge has been crossed, over every recorded ride: the passport. */
fun stamps(rides: List<RideSummary>): Map<String, Int> =
    rides.flatMap { it.crossings }.groupingBy { it.substringBefore(' ') }.eachCount()

/** A plan to ride a recorded ride again. Null when that ride is too short to follow. */
fun planFromRide(ride: RideSummary, data: AppData): RidePlan? {
    val line = RideStore.line(ride) ?: return null
    return RidePlan.of("again-${ride.startMs}", ride.meta.name ?: "Ride again", "Where that ride started", ride.name, line.points, data.ends, data.infos)
}

// ---------------------------------------------------------------- Plan

/**
 * Plan (release 0.3): pick a line to follow. Two kinds exist so far: the rides shipped in the app
 * and rides the rider recorded. Building a route from stops needs on-phone routing (0.4), so those
 * parts are drawn and labeled "Not built yet".
 */
@Composable
fun PlanSheet(app: AppState, data: AppData, rides: List<RideSummary>, onHeight: (Int) -> Unit, onGo: (RidePlan) -> Unit) {
    val plan = app.plan
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler { if (app.plan != null) app.plan = null else app.planning = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Panel(Modifier.fillMaxWidth().fillMaxHeight(0.6f).padding(start = 8.dp, end = 4.dp, bottom = 4.dp).onSizeChanged { onHeight(it.height) }) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (plan == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Plan a ride", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        GlyphButton(back = false, description = "Close plan") { app.planning = false }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Built-in rides", fontWeight = FontWeight.Bold)
                        data.rides.forEach { p -> PlanCard(p.name, p.summary(), "Starts: ${p.start}") { app.plan = p } }
                        Text("Ride one of yours again", fontWeight = FontWeight.Bold)
                        val mine = rides.filter { it.distanceM >= 200 }.take(5)
                        if (mine.isEmpty()) Text("No recorded rides yet. Record one and it shows up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        mine.forEach { r ->
                            PlanCard(r.name, Units.miles(r.distanceM) + bridgeWords(r.crossings.size), null) {
                                scope.launch {
                                    val made = withContext(Dispatchers.Default) { planFromRide(r, data) }
                                    if (made == null) message = "That ride is too short to follow." else { message = null; app.plan = made }
                                }
                            }
                        }
                        message?.let { Warning(it) }
                        NotBuilt("Themed rides (food cart loop, mural hunt). Due in 0.8.")
                        NotBuilt("A route to any address or bridge you pick. Due in 0.4 and 0.5.")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlyphButton(back = true, description = "Back to the list of rides") { app.plan = null }
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleLarge)
                            Text(plan.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Stops first: which bridges, which way, which side. The description comes after.
                        StopRow("START", plan.start.ifBlank { "Start of the line" }, null, null)
                        plan.bridges.forEach { b ->
                            StopRow("BRIDGE · ${b.direction.uppercase()}" + (b.side?.let { " · ${it.uppercase()} SIDE" } ?: ""), b.label, b.bridgeId, null)
                        }
                        StopRow("END", if (plan.line.isLoop) "Back at the start" else "End of the line", null, null)
                        if (plan.note.isNotBlank()) Text(plan.note, fontSize = 14.sp)
                        NotBuilt("Add, remove or reorder stops. Due in 0.5.")
                    }
                    // Go stays in reach below the list, however long the list is.
                    BigButton("Go") { onGo(plan) }
                }
            }
        }
    }
}

private fun bridgeWords(n: Int) = when (n) { 0 -> ""; 1 -> " · 1 bridge"; else -> " · $n bridges" }

@Composable
private fun PlanCard(title: String, line: String, sub: String?, onClick: () -> Unit) {
    CardButton(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            if (sub != null) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StopRow(overline: String, title: String, bridgeId: String?, trailing: String?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
            if (bridgeId != null) BridgeIcon(bridgeId, Modifier.width(72.dp))
            else Tag(overline.substringBefore(' '))
        }
        Column(Modifier.weight(1f)) {
            Text(overline, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
        if (trailing != null) Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- Ride

@Composable
fun GpsLine(s: FollowState) {
    val (text, warn) = when (s.gps) {
        GpsState.WAITING -> "Waiting for GPS" to true
        GpsState.GOOD -> "GPS good · ±${s.accuracyM?.toInt() ?: 0} m" to false
        GpsState.WEAK -> "GPS weak · ±${s.accuracyM?.toInt() ?: 0} m" to true
        GpsState.ESTIMATED -> "GPS estimated · last fix ${s.lastFixAgeMs / 1000} s ago" to true
    }
    // Good is the good-state color; a problem is the warning color with its icon, never color alone.
    val bg = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    val fg = if (warn) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondary
    Row(
        Modifier.clip(lookShape()).background(bg).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (warn) Icon(painterResource(R.drawable.ic_warning), contentDescription = "Warning", tint = fg, modifier = Modifier.size(20.dp))
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** The big words at the top of the ride: next bridge, off the line, and so on (core Banner). */
@Composable
private fun BannerPanel(b: Banner) {
    val look = LocalLook.current
    // E: ink sign with cream text. C: the green sign from the C ride board. A warning is the warning color plus its icon.
    val bg = when { b.warn -> MaterialTheme.colorScheme.error; look.dark -> PdxC.Green; else -> PdxE.Ink }
    val fg = when { b.warn -> MaterialTheme.colorScheme.onError; look.dark -> PdxC.Asphalt; else -> PdxE.Cream }
    Row(
        Modifier.fillMaxWidth().clip(lookShape()).background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val bridgeId = b.bridgeId
        when {
            b.warn -> Icon(painterResource(R.drawable.ic_warning), contentDescription = "Warning", tint = fg, modifier = Modifier.size(44.dp))
            bridgeId != null -> Box(
                Modifier.clip(lookShape()).background(if (look.dark) PdxC.Asphalt else Color.White).padding(6.dp),
            ) { BridgeIcon(bridgeId, Modifier.width(84.dp), detail = 2) }
        }
        Column {
            Text(b.big, color = fg, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, fontFamily = look.numbers, maxLines = 1)
            Text(b.title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            b.sub?.let { Text(it, color = fg, fontSize = 15.sp) }
        }
    }
}

/**
 * The ride screen (tasks 1.4 to 1.6). Everything a hand touches is 64 dp tall, End needs a 1 s hold,
 * and Lock makes the screen ignore touches until Unlock is held: rain and gloves (0.3).
 */
@Composable
fun RideOverlay(status: RecorderStatus, follow: FollowState, plan: RidePlan?, controller: MapController, onEnd: () -> Unit) {
    var following by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var topPx by remember { mutableStateOf(0) }
    var bottomPx by remember { mutableStateOf(0) }

    // The clock that turns the marker "estimated" and coasts it through GPS gaps.
    LaunchedEffect(Unit) { while (true) { Nav.tick(); now = System.currentTimeMillis(); delay(500) } }
    BackHandler { /* a ride ends with End, not with the back button */ }

    DisposableEffect(controller) {
        controller.onUserMove = { following = false }
        onDispose { controller.onUserMove = {} }
    }
    LaunchedEffect(topPx, bottomPx) { controller.setPadding(topPx, bottomPx) }
    val hasPosition = follow.position != null
    LaunchedEffect(hasPosition) { if (hasPosition && following) controller.follow() }
    LaunchedEffect(follow.position, follow.headingDeg, follow.gps) {
        val at = follow.position
        // Line direction up when on a line (steady); otherwise the way the rider is going.
        controller.setMarker(at?.let { MarkerState(it, follow.headingDeg, follow.gps != GpsState.GOOD) }, follow.routeBearingDeg ?: follow.headingDeg)
    }
    Compass(follow)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(12.dp).onSizeChanged { topPx = it.height },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BannerPanel(Banner.of(plan, follow, status.distanceM))
            GpsLine(follow)
        }
        if (!following && hasPosition) {
            TallButton("Re-center", Modifier.align(Alignment.CenterEnd).padding(12.dp)) { following = true; controller.follow() }
        }
        Panel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 8.dp).onSizeChanged { bottomPx = it.height }) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val elapsed = status.startMs?.let { now - it }?.coerceAtLeast(0) ?: 0L
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The number big, its unit small underneath: three of them fit side by side on any phone.
                    if (plan != null) Stat("mi left", "%.1f".format((follow.remainingM ?: plan.lengthM) / 1609.344), Modifier.weight(1f))
                    else Stat("mi ridden", "%.1f".format(status.distanceM / 1609.344), Modifier.weight(1f))
                    Stat("mph", "%.0f".format(follow.speedMps * 2.236936), Modifier.weight(1f))
                    Stat("time", "%d:%02d".format(elapsed / 60_000, (elapsed / 1000) % 60), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TallButton("Lock", Modifier.weight(1f)) { locked = true }
                    HoldButton("End", "hold 1 s", "Hold for one second to end the ride", Modifier.weight(2f), onEnd)
                }
            }
        }
        if (locked) {
            // Swallows every touch, including map pans, until Unlock is held for a second.
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).pointerInput(Unit) { detectTapGestures { } }
                    .semantics { contentDescription = "Screen locked" },
            ) {
                Box(Modifier.align(Alignment.Center)) { Tag("Screen locked. Hold Unlock to use it.") }
                HoldButton(
                    "Unlock", "hold 1 s", "Hold for one second to unlock the screen",
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 26.dp, end = 26.dp, bottom = 22.dp),
                ) { locked = false }
            }
        }
    }
}

/**
 * Feeds the phone's compass to the follower while a ride runs (task 1.4). The follower only uses it
 * when the rider is stopped. The sensor reads magnetic north; Portland's is about 15 degrees east of
 * true north, so the reading is corrected for where the rider is.
 */
@Composable
private fun Compass(follow: FollowState) {
    val context = LocalContext.current
    val at = follow.position
    val declination = remember(at != null) {
        at?.let { GeomagneticField(it.lat.toFloat(), it.lon.toFloat(), 0f, System.currentTimeMillis()).declination } ?: 0f
    }
    DisposableEffect(declination) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var lastMs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastMs < 500) return // twice a second is enough for a stopped rider
                lastMs = nowMs
                SensorManager.getRotationMatrixFromVector(matrix, e.values)
                SensorManager.getOrientation(matrix, angles)
                Nav.onCompass(Math.toDegrees(angles[0].toDouble()) + declination)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }
}

// ---------------------------------------------------------------- Bridge sheet

/** Tap a bridge dot: what the bridge is, whether bikes can cross, and which side to ride. */
@Composable
fun BridgeSheet(info: BridgeInfo, crossed: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } }, contentAlignment = Alignment.BottomCenter) {
        Panel(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, bottom = 4.dp).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(info.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    GlyphButton(back = false, description = "Close bridge details", onClick = onClose)
                }
                Box(Modifier.fillMaxWidth().clip(lookShape()).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    BridgeIcon(info.id, Modifier.width(240.dp), detail = 3, label = "${info.name} icon")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (info.access) {
                        BikeAccess.NO -> Tag("No bikes", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                        BikeAccess.LIMITED -> Tag("Caution", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                        BikeAccess.YES -> Tag("Bikes welcome")
                    }
                    if (info.year.isNotBlank()) Tag("Built ${info.year}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface)
                }
                Text("${cap(info.type)}. ${info.category}.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(info.facility + if (info.facility.endsWith(".")) "" else ".")
                if (info.summary.isNotBlank() && info.summary != info.facility) Text(info.summary)
                info.crossings.filter { it.offered }.forEach { c ->
                    val side = info.sideFor(c.direction)
                    Text(cap(c.direction) + ": " + (side?.let { "ride the $it side" } ?: "side not confirmed in the data yet"), fontWeight = FontWeight.SemiBold)
                }
                if (info.access != BikeAccess.NO) {
                    Text(
                        when (crossed) { 0 -> "Passport: not crossed yet."; 1 -> "Passport: crossed once."; else -> "Passport: crossed $crossed times." },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NotBuilt("Route me over this bridge. Due in 0.5.")
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Summary

/**
 * Shown when a ride ends, and from the ride log. Bridges crossed are found from the recorded track
 * (core Crossings); the rating is saved beside the ride for the rider profile (0.7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryScreen(file: File, data: AppData, rides: List<RideSummary>, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ride by produceState<RideSummary?>(null, file) { value = withContext(Dispatchers.IO) { RideStore.load(file, data.ends) } }
    var rating by remember(file) { mutableStateOf<String?>(null) }
    var reasons by remember(file) { mutableStateOf(emptyList<String>()) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ride) { ride?.let { rating = it.meta.rating; reasons = it.meta.reasons } }
    BackHandler(onBack = onDone)

    fun save(newRating: String?, newReasons: List<String>) {
        rating = newRating; reasons = newReasons
        scope.launch(Dispatchers.IO) { RideStore.writeMeta(file, RideStore.readMeta(file).copy(rating = newRating, reasons = newReasons)) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri: Uri? ->
        val r = ride
        if (uri != null && r != null) scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(RideStore.gpx(r).toByteArray()) }
            withContext(Dispatchers.Main) { note = "GPX saved." }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val r = ride
            if (r == null) { Text("Reading the ride"); return@Column }
            Text("RIDE SAVED", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(r.name, style = MaterialTheme.typography.headlineSmall)
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val movingS = r.movingMs / 1000
                    Stat("miles", "%.1f".format(r.distanceM / 1609.344), Modifier.weight(1f))
                    Stat("moving", "%d:%02d".format(movingS / 60, movingS % 60), Modifier.weight(1f))
                    Stat("mph average", if (movingS > 0) "%.0f".format(r.distanceM / movingS * 2.236936) else "0", Modifier.weight(1f))
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val all = stamps(rides.filter { it.file != file } + r)
                    val before = stamps(rides.filter { it.file != file })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bridges crossed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Tag("Passport ${all.keys.count { id -> data.bikeable.any { it.id == id } }} of ${data.bikeable.size}")
                    }
                    if (r.crossings.isEmpty()) Text("None on this ride.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.crossings.forEachIndexed { i, key ->
                        val id = key.substringBefore(' ')
                        val firstTime = id !in before && r.crossings.indexOfFirst { it.substringBefore(' ') == id } == i
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BridgeIcon(id, Modifier.width(72.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(data.infoById[id]?.label ?: id, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                Text(key.substringAfter(' '), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                                if (firstTime) Tag("New stamp")
                            }
                        }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("How was the route?", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Good", "OK", "Bad").forEach { label ->
                            Choice(label, rating == label, Modifier.weight(1f)) { save(if (rating == label) null else label, reasons) }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Safer", "Prettier", "Faster", "Flatter", "Quieter").forEach { label ->
                            Choice(label, label in reasons) { save(rating, if (label in reasons) reasons - label else reasons + label) }
                        }
                    }
                }
            }
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("GPS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${r.fixes} fixes. " + (
                            if (r.gaps.gapsOver10s == 0) "No gaps over 10 s while moving."
                            else "${r.gaps.gapsOver10s} gaps over 10 s while moving, longest ${r.gaps.longestGapMs / 1000} s."
                            ) + if (r.gaps.stoppedPauses > 0) " ${r.gaps.stoppedPauses} quiet spells while stopped (Android slows GPS when the phone is still; not counted)." else "",
                    )
                }
            }
            NotBuilt("Questions about streets you chose instead of the suggested ones. Due with the rider profile, 0.7.")
            note?.let { Text(it, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold) }
        }
        // Export and Done stay in reach below the list.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TallButton("Export GPX", Modifier.weight(1f)) { exporter.launch(file.name.replace(".csv", ".gpx")) }
            Box(Modifier.weight(1f)) { BigButton("Done", onClick = onDone) }
        }
    }
}

/** A choice chip that shows it is picked with a filled background and a check, not color alone. */
@Composable
private fun Choice(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = LocalLook.current
    Box(
        modifier.heightIn(min = 48.dp).clip(lookShape())
            .background(if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .border(look.border, if (look.dark) MaterialTheme.colorScheme.outline else look.borderColor, lookShape())
            .semantics { selected = on }
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (if (on) "✓ " else "") + label, fontWeight = FontWeight.Bold,
            color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}
