package pdx.bridgeride.app

import android.content.Context
import pdx.bridgeride.core.Fix
import pdx.bridgeride.core.FixLine
import pdx.bridgeride.core.GapReport
import pdx.bridgeride.core.Gpx
import pdx.bridgeride.core.RideStats
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A recorded ride: one fix-per-line file in the app's private folder (rides/). */
data class RideSummary(
    val file: File,
    val name: String,
    val startMs: Long?,
    val fixes: Int,
    val distanceM: Double,
    val gaps: GapReport,
)

object RideStore {
    private val nameFormat = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a")

    fun dir(context: Context): File = File(context.filesDir, "rides").apply { mkdirs() }

    fun newRideFile(context: Context, startMs: Long): File = File(dir(context), "ride-$startMs.csv")

    fun read(file: File): List<Fix> = file.useLines { FixLine.decodeAll(it.toList()) }

    fun nameFor(startMs: Long?): String =
        startMs?.let { "Ride, " + nameFormat.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
            ?: "Ride"

    fun list(context: Context): List<RideSummary> =
        dir(context).listFiles { f -> f.name.endsWith(".csv") }.orEmpty()
            .sortedByDescending { it.name }
            .map { f ->
                val fixes = read(f)
                val start = f.name.removePrefix("ride-").removeSuffix(".csv").toLongOrNull()
                RideSummary(f, nameFor(start), start, fixes.size, RideStats.distanceM(fixes), GapReport.of(fixes))
            }

    fun gpx(summary: RideSummary): String = Gpx.write(read(summary.file), summary.name)
}
