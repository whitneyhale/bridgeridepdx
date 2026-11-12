package pdx.bridgeride.app

import android.content.Context
import pdx.bridgeride.core.BridgeEnd
import pdx.bridgeride.core.Crossings
import pdx.bridgeride.core.Fix
import pdx.bridgeride.core.FixLine
import pdx.bridgeride.core.GapReport
import pdx.bridgeride.core.Gpx
import pdx.bridgeride.core.LatLon
import pdx.bridgeride.core.MovingTime
import pdx.bridgeride.core.RideMeta
import pdx.bridgeride.core.RideStats
import pdx.bridgeride.core.RouteLine
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A recorded ride: one fix-per-line file in the app's private folder (rides/), and a small .meta file beside it. */
data class RideSummary(
    val file: File,
    val name: String,
    val startMs: Long?,
    val fixes: Int,
    val distanceM: Double,
    val movingMs: Long,
    val gaps: GapReport,
    val meta: RideMeta,
    /** "bridge-id direction" for every bridge crossed, in order. */
    val crossings: List<String>,
)

object RideStore {
    private val nameFormat = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")

    fun dir(context: Context): File = File(context.filesDir, "rides").apply { mkdirs() }

    fun newRideFile(context: Context, startMs: Long): File = File(dir(context), "ride-$startMs.csv")

    fun read(file: File): List<Fix> = file.useLines { FixLine.decodeAll(it.toList()) }

    private fun metaFile(ride: File) = File(ride.parentFile, ride.name.removeSuffix(".csv") + ".meta")

    fun readMeta(ride: File): RideMeta = metaFile(ride).takeIf { it.exists() }?.let { RideMeta.decode(it.readText()) } ?: RideMeta()

    fun writeMeta(ride: File, meta: RideMeta) = metaFile(ride).writeText(meta.encode())

    private fun whenFor(startMs: Long) = nameFormat.format(Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()))

    /** "Ride, Fri Nov 13, 6:42 PM", or the plan's name in place of "Ride" when the ride followed one. */
    fun nameFor(startMs: Long?, meta: RideMeta = RideMeta()): String {
        val what = meta.name ?: "Ride"
        return if (startMs == null) what else "$what, ${whenFor(startMs)}"
    }

    /** The good fixes of a ride as plain points: what crossings and "ride again" lines are made from. */
    private fun track(fixes: List<Fix>): List<LatLon> =
        fixes.filter { it.accuracyM <= RideStats.MAX_ACCURACY_FOR_DISTANCE_M }.map { LatLon(it.lat, it.lon) }

    /**
     * Reads one ride. Crossings are worked out once and kept in the .meta file, so the passport does
     * not re-check every ride each time. A ride still being recorded is not cached: it is not finished.
     */
    fun load(file: File, ends: List<BridgeEnd>, stillRecording: Boolean = false): RideSummary {
        val fixes = read(file)
        val start = file.name.removePrefix("ride-").removeSuffix(".csv").toLongOrNull()
        var meta = readMeta(file)
        val crossings = meta.crossings ?: Crossings.detect(track(fixes), ends).map(RideMeta::crossingKey).also {
            if (!stillRecording) { meta = meta.copy(crossings = it); writeMeta(file, meta) }
        }
        return RideSummary(file, nameFor(start, meta), start, fixes.size, RideStats.distanceM(fixes), MovingTime.ms(fixes), GapReport.of(fixes), meta, crossings)
    }

    fun list(context: Context, ends: List<BridgeEnd>, recordingStartMs: Long? = null): List<RideSummary> =
        dir(context).listFiles { f -> f.name.endsWith(".csv") }.orEmpty()
            .sortedByDescending { it.name }
            .map { f -> load(f, ends, stillRecording = recordingStartMs != null && f.name == "ride-$recordingStartMs.csv") }

    /** The line of a recorded ride, to ride it again. Null when the ride is too short. */
    fun line(summary: RideSummary): RouteLine? = RouteLine.fromFixes(read(summary.file))

    fun gpx(summary: RideSummary): String = Gpx.write(read(summary.file), summary.name)
}
