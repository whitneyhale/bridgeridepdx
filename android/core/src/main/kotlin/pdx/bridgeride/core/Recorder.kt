package pdx.bridgeride.core

import java.time.Instant
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS reading. Times are UTC milliseconds. */
data class Fix(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedMps: Float?,
    val bearingDeg: Float?,
)

/**
 * One fix per line, comma separated, so a ride file survives a crash:
 * at worst the last half-written line is dropped.
 * time,lat,lon,accuracy,speed,bearing (speed and bearing may be empty)
 */
object FixLine {
    fun encode(f: Fix): String = String.format(
        Locale.US, "%d,%.7f,%.7f,%.1f,%s,%s",
        f.timeMs, f.lat, f.lon, f.accuracyM,
        f.speedMps?.let { String.format(Locale.US, "%.2f", it) } ?: "",
        f.bearingDeg?.let { String.format(Locale.US, "%.1f", it) } ?: "",
    )

    fun decode(line: String): Fix? {
        val p = line.split(',')
        if (p.size != 6) return null
        return try {
            Fix(
                timeMs = p[0].toLong(),
                lat = p[1].toDouble(),
                lon = p[2].toDouble(),
                accuracyM = p[3].toFloat(),
                speedMps = p[4].takeIf { it.isNotEmpty() }?.toFloat(),
                bearingDeg = p[5].takeIf { it.isNotEmpty() }?.toFloat(),
            )
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun decodeAll(lines: Iterable<String>): List<Fix> = lines.mapNotNull(::decode)
}

/** Gaps between fixes. A gap over 10 s fails the S1 bike test. */
data class GapReport(
    val fixes: Int,
    val gapsOver10s: Int,
    val longestGapMs: Long,
    val longestGapStartMs: Long?,
) {
    companion object {
        const val LIMIT_MS = 10_000L

        fun of(fixes: List<Fix>): GapReport {
            var over = 0
            var longest = 0L
            var longestStart: Long? = null
            for (i in 1 until fixes.size) {
                val gap = fixes[i].timeMs - fixes[i - 1].timeMs
                if (gap > LIMIT_MS) over++
                if (gap > longest) {
                    longest = gap
                    longestStart = fixes[i - 1].timeMs
                }
            }
            return GapReport(fixes.size, over, longest, longestStart)
        }
    }
}

object RideStats {
    /** Fixes worse than this are ignored for distance; they are still saved. */
    const val MAX_ACCURACY_FOR_DISTANCE_M = 25f
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun haversineM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    fun distanceM(fixes: List<Fix>): Double {
        var total = 0.0
        var last: Fix? = null
        for (f in fixes) {
            if (f.accuracyM > MAX_ACCURACY_FOR_DISTANCE_M) continue
            last?.let { total += haversineM(it.lat, it.lon, f.lat, f.lon) }
            last = f
        }
        return total
    }
}

/** Time spent moving, for the ride summary. */
object MovingTime {
    /** Slower than this between two fixes counts as stopped (a light, a bridge lift, a photo). */
    const val MIN_SPEED_MPS = 1.0
    /** A longer silence than this is a gap, not riding: it is not counted either way. */
    const val MAX_STEP_MS = 10_000L

    fun ms(fixes: List<Fix>): Long {
        var total = 0L
        var last: Fix? = null
        for (f in fixes) {
            if (f.accuracyM > RideStats.MAX_ACCURACY_FOR_DISTANCE_M) continue
            val prev = last
            last = f
            if (prev == null) continue
            val dt = f.timeMs - prev.timeMs
            if (dt <= 0 || dt > MAX_STEP_MS) continue
            val speed = RideStats.haversineM(prev.lat, prev.lon, f.lat, f.lon) / (dt / 1000.0)
            if (speed >= MIN_SPEED_MPS) total += dt
        }
        return total
    }
}

/** GPX 1.1 track, readable by Strava, Ride with GPS, Garmin and gpx.studio. */
object Gpx {
    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    fun write(fixes: List<Fix>, name: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Bridge Ride PDX\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk>\n    <name>").append(esc(name)).append("</name>\n    <trkseg>\n")
        for (f in fixes) {
            append(String.format(Locale.US, "      <trkpt lat=\"%.7f\" lon=\"%.7f\">", f.lat, f.lon))
            append("<time>").append(Instant.ofEpochMilli(f.timeMs).toString()).append("</time>")
            append("</trkpt>\n")
        }
        append("    </trkseg>\n  </trk>\n</gpx>\n")
    }
}
