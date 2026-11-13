package pdx.bridgeride.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pdx.bridgeride.core.BikeAccess
import pdx.bridgeride.core.BridgeInfo
import pdx.bridgeride.core.Units

/** Library: the ride log and the bridge passport. */
@Composable
fun LibraryTab(app: AppState, data: AppData, rides: List<RideSummary>, recordingStartMs: Long?) {
    var page by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Segmented(listOf("Rides", "Passport"), page) { page = it }
        if (page == 0) RideLog(app, data, rides, recordingStartMs) else Passport(app, data, stamps(rides))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RideLog(app: AppState, data: AppData, rides: List<RideSummary>, recordingStartMs: Long?) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    Text("Ride log", style = MaterialTheme.typography.headlineMedium)
    message?.let { Warning(it) }
    if (rides.isEmpty()) Text("No rides yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    rides.forEach { ride ->
        val live = ride.startMs != null && ride.startMs == recordingStartMs
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ride.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                val bridges = when (ride.crossings.size) { 0 -> "no bridges"; 1 -> "1 bridge"; else -> "${ride.crossings.size} bridges" }
                val gaps = if (ride.gaps.gapsOver10s == 0) "no gaps over 10 s" else "${ride.gaps.gapsOver10s} gaps over 10 s"
                Text(
                    listOfNotNull(Units.miles(ride.distanceM), bridges, gaps, ride.meta.rating?.let { "rated $it" }).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
                if (!live) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Details") { app.summary = ride.file }
                    SmallButton("Ride again") {
                        scope.launch {
                            val plan = withContext(Dispatchers.Default) { planFromRide(ride, data) }
                            if (plan == null) message = "That ride is too short to follow."
                            else { message = null; app.plan = plan; app.planning = true; app.tab = Tab.MAP }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bridge passport: one stamp for each bikeable bridge the rider has crossed in a recorded ride.
 * Stamps come from the recorded tracks (core Crossings), so they cannot be earned by riding past.
 */
@Composable
private fun Passport(app: AppState, data: AppData, stamps: Map<String, Int>) {
    val got = data.bikeable.count { it.id in stamps }
    val total = data.bikeable.size
    val look = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Bridge passport", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        Tag("$got of $total")
    }
    // Progress as a bar and as the count beside it, never the bar alone.
    Box(
        Modifier.fillMaxWidth().height(14.dp).clip(lookShape()).background(MaterialTheme.colorScheme.surface)
            .border(look.border, look.borderColor, lookShape()).semantics { contentDescription = "$got of $total bridges stamped" },
    ) {
        Box(Modifier.fillMaxWidth(if (total == 0) 0f else got / total.toFloat()).height(14.dp).background(MaterialTheme.colorScheme.primary))
    }
    Text("Cross a bridge on a recorded ride to stamp it. Tap a bridge for its details.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

    val stamped = data.bikeable.filter { it.id in stamps }
    val open = data.bikeable.filter { it.id !in stamps }
    val closed = data.infos.filter { it.access == BikeAccess.NO }
    if (stamped.isNotEmpty()) { Text("Stamped", fontWeight = FontWeight.Bold); StampGrid(stamped, stamps, true) { app.bridge = it } }
    if (open.isNotEmpty()) { Text("Still to cross", fontWeight = FontWeight.Bold); StampGrid(open, stamps, false) { app.bridge = it } }
    if (closed.isNotEmpty()) { Text("No bikes allowed", fontWeight = FontWeight.Bold); StampGrid(closed, stamps, false) { app.bridge = it } }
}

@Composable
private fun StampGrid(list: List<BridgeInfo>, stamps: Map<String, Int>, stamped: Boolean, onTap: (String) -> Unit) {
    list.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { info -> Stamp(info, stamps[info.id] ?: 0, stamped, Modifier.weight(1f)) { onTap(info.id) } }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Stamp(info: BridgeInfo, count: Int, stamped: Boolean, modifier: Modifier, onTap: () -> Unit) {
    val look = LocalLook.current
    val shape = lookShape()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val words = when {
        info.access == BikeAccess.NO -> "${info.label}, no bikes"
        stamped -> "${info.label}, stamped, crossed $count ${if (count == 1) "time" else "times"}"
        else -> "${info.label}, not crossed yet"
    }
    // Stamped: a solid sticker in the bridge's own colors. Not yet: a dashed outline. Shape and words, not color alone.
    val frame = if (stamped) Modifier.background(MaterialTheme.colorScheme.surface, shape).border(look.border, look.borderColor, shape)
    else Modifier.drawBehind {
        drawRoundRect(
            muted, cornerRadius = CornerRadius(look.corner.toPx()),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
    }
    Column(
        modifier.clip(shape).then(frame).clickable(role = Role.Button, onClick = onTap)
            .semantics(mergeDescendants = true) { contentDescription = words }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BridgeIcon(info.id, Modifier.fillMaxWidth(), ink = if (stamped) BridgeInk.FULL else BridgeInk.GHOST, detail = 2)
        Text(info.label, fontSize = 13.sp, fontWeight = if (stamped) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
            color = if (stamped) MaterialTheme.colorScheme.onSurface else muted)
        Text(
            when { info.access == BikeAccess.NO -> "no bikes"; stamped -> "×$count"; else -> "not yet" },
            fontSize = 12.sp, color = muted, maxLines = 1,
        )
    }
}
