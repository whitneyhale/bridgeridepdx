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
    /** Silences longer than 10 s during which the rider moved: GPS was really lost. */
    val gapsOver10s: Int,
    /** The longest of those, or the longest ordinary step between fixes when there were none. */
    val longestGapMs: Long,
    val longestGapStartMs: Long?,
    /**
     * Silences longer than 10 s with the rider in the same place before and after. Android slows GPS
     * updates when the phone is not moving, so these are expected at a desk or a long light.
     */
    val stoppedPauses: Int = 0,
) {
    companion object {
        const val LIMIT_MS = 10_000L
        /** Moved less than this across a silence: the rider was stopped, GPS was not lost. */
        const val STOPPED_M = 30.0

        /** True when the silence between two fixes is a stop, not a loss. */
        fun isStoppedPause(before: Fix, after: Fix): Boolean =
            after.timeMs - before.timeMs > LIMIT_MS &&
                RideStats.haversineM(before.lat, before.lon, after.lat, after.lon) <= STOPPED_M

        fun of(fixes: List<Fix>): GapReport {
            var over = 0
            var pauses = 0
            var longest = 0L
            var longestStart: Long? = null
            for (i in 1 until fixes.size) {
                val gap = fixes[i].timeMs - fixes[i - 1].timeMs
                if (isStoppedPause(fixes[i - 1], fixes[i])) { pauses++; continue }
                if (gap > LIMIT_MS) over++
                if (gap > longest) {
                    longest = gap
                    longestStart = fixes[i - 1].timeMs
                }
            }
            return GapReport(fixes.size, over, longest, longestStart, pauses)
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

    fun distanceM(fixes: List<Fix>): Double = Odometer().also { o -> fixes.forEach(o::add) }.meters
}

/**
 * Adds up distance ridden, one fix at a time. A phone that is not moving still reports positions
 * that wander by a few meters, and adding those up gave 1.6 miles for 150 minutes at a desk
 * (S24, 2026-11-12). So a fix only counts when all three hold:
 * its accuracy is 25 m or better; the phone does not report it as standing still (under 0.5 m/s);
 * and it is at least 10 m, and at least its own accuracy, from the last counted fix.
 * The last counted fix stays put through a stop, so the first fix after it adds the real distance moved.
 */
class Odometer {
    var meters = 0.0
        private set
    private var anchor: Fix? = null

    fun add(f: Fix): Double {
        if (f.accuracyM > RideStats.MAX_ACCURACY_FOR_DISTANCE_M) return meters
        val from = anchor
        if (from == null) { anchor = f; return meters }
        val speed = f.speedMps
        if (speed != null && speed < STILL_MPS) return meters
        val d = RideStats.haversineM(from.lat, from.lon, f.lat, f.lon)
        if (d < maxOf(MIN_STEP_M, f.accuracyM.toDouble())) return meters
        meters += d
        anchor = f
        return meters
    }

    companion object {
        const val STILL_MPS = 0.5f
        const val MIN_STEP_M = 10.0
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
            // The phone's own speed when it gives one: positions wander at a standstill, its speed reading does not.
            val speed = f.speedMps?.toDouble() ?: (RideStats.haversineM(prev.lat, prev.lon, f.lat, f.lon) / (dt / 1000.0))
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
