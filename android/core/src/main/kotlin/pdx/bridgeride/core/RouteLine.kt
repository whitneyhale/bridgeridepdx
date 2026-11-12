package pdx.bridgeride.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(a: LatLon, b: LatLon): Double = RideStats.haversineM(a.lat, a.lon, b.lat, b.lon)

    /** Compass bearing from a to b, 0 = north, 90 = east, in [0, 360). */
    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val la = Math.toRadians(a.lat)
        val lb = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lb)
        val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dLon)
        return norm(Math.toDegrees(atan2(y, x)))
    }

    fun norm(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Signed smallest turn from a to b, in (-180, 180]. Positive is clockwise. */
    fun turnDeg(from: Double, to: Double): Double {
        val d = norm(to - from)
        return if (d > 180.0) d - 360.0 else d
    }

    /** East and north offsets in meters of p from origin. Flat-earth, fine across a city. */
    fun localXY(origin: LatLon, p: LatLon): Pair<Double, Double> {
        val x = Math.toRadians(p.lon - origin.lon) * cos(Math.toRadians(origin.lat)) * EARTH_RADIUS_M
        val y = Math.toRadians(p.lat - origin.lat) * EARTH_RADIUS_M
        return x to y
    }

    /** The point reached from origin after going meters along bearingDeg. */
    fun offset(origin: LatLon, bearingDeg: Double, meters: Double): LatLon {
        val b = bearingDeg * PI / 180.0
        val dLat = meters * cos(b) / EARTH_RADIUS_M
        val dLon = meters * sin(b) / (EARTH_RADIUS_M * cos(Math.toRadians(origin.lat)))
        return LatLon(origin.lat + Math.toDegrees(dLat), origin.lon + Math.toDegrees(dLon))
    }
}

/** Where a position falls on a line: how far along, how far off to the side. */
data class Projection(
    val alongM: Double,
    val offsetM: Double,
    val point: LatLon,
    val bearingDeg: Double,
)

/**
 * The line a ride follows (task 1.6). Distances along it are meters from the first point.
 * Lookups take a window of distances, so a loop or an out-and-back that passes the same
 * spot twice is matched to the pass the rider is actually on.
 */
class RouteLine(points: List<LatLon>) {
    /** Points with exact repeats removed, so every segment has a length. */
    val points: List<LatLon> = points.fold(mutableListOf()) { acc, p -> if (acc.lastOrNull() != p) acc += p; acc }
    private val cum: DoubleArray = DoubleArray(this.points.size)

    init {
        require(this.points.size >= 2) { "a line needs two different points" }
        for (i in 1 until this.points.size) cum[i] = cum[i - 1] + Geo.distanceM(this.points[i - 1], this.points[i])
    }

    val lengthM: Double get() = cum.last()

    /** Distance along the line at point index i. */
    fun alongAtIndex(i: Int): Double = cum[i.coerceIn(0, cum.size - 1)]

    /** True when the line ends where it started (within 60 m). */
    val isLoop: Boolean get() = Geo.distanceM(points.first(), points.last()) <= 60.0

    private fun segmentAt(alongM: Double): Int {
        val a = alongM.coerceIn(0.0, lengthM)
        var lo = 0
        var hi = cum.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cum[mid] <= a) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun pointAt(alongM: Double): LatLon {
        val a = alongM.coerceIn(0.0, lengthM)
        val i = segmentAt(a)
        val len = cum[i + 1] - cum[i]
        val t = if (len <= 0.0) 0.0 else (a - cum[i]) / len
        val p = points[i]
        val q = points[i + 1]
        return LatLon(p.lat + (q.lat - p.lat) * t, p.lon + (q.lon - p.lon) * t)
    }

    fun bearingAt(alongM: Double): Double {
        val i = segmentAt(alongM)
        return Geo.bearingDeg(points[i], points[i + 1])
    }

    /**
     * Nearest point of the line to p, looking only between fromM and toM along the line.
     * When two places are equally near (within 5 m), the one closest to preferNearM wins,
     * or the earlier one when no preference is given.
     */
    fun project(p: LatLon, fromM: Double = 0.0, toM: Double = lengthM, preferNearM: Double? = null): Projection =
        search(p, fromM, toM, preferNearM, null)!!

    /**
     * Like [project], but only looks at stretches of the line that run the way the rider is going
     * (within 100 degrees of headingDeg). On a ramp that doubles back, or an out-and-back on one
     * street, two parts of the line are a few meters apart and only the direction tells them apart.
     * Null when no part of the window runs that way.
     */
    fun projectAlongHeading(p: LatLon, headingDeg: Double, fromM: Double = 0.0, toM: Double = lengthM, preferNearM: Double? = null): Projection? =
        search(p, fromM, toM, preferNearM, headingDeg)

    private fun search(p: LatLon, fromM: Double, toM: Double, preferNearM: Double?, headingDeg: Double?): Projection? {
        val from = fromM.coerceIn(0.0, lengthM)
        val to = toM.coerceIn(from, lengthM)
        var best: Projection? = null
        val first = segmentAt(from)
        val last = segmentAt(to)
        for (i in first..last) {
            val bearing = Geo.bearingDeg(points[i], points[i + 1])
            if (headingDeg != null && abs(Geo.turnDeg(headingDeg, bearing)) > HEADING_TOLERANCE_DEG) continue
            val (ax, ay) = Geo.localXY(p, points[i])
            val (bx, by) = Geo.localXY(p, points[i + 1])
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            var t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val segLen = cum[i + 1] - cum[i]
            // Keep the answer inside the window.
            val tMin = if (segLen <= 0.0) 0.0 else ((from - cum[i]) / segLen).coerceIn(0.0, 1.0)
            val tMax = if (segLen <= 0.0) 1.0 else ((to - cum[i]) / segLen).coerceIn(0.0, 1.0)
            t = t.coerceIn(tMin, tMax)
            val off = hypot(ax + dx * t, ay + dy * t)
            val along = cum[i] + segLen * t
            val cur = best
            val clearlyNearer = cur == null || off < cur.offsetM - 5.0
            val tieNearerPreference = cur != null && preferNearM != null && off <= cur.offsetM + 5.0 &&
                abs(along - preferNearM) < abs(cur.alongM - preferNearM)
            if (clearlyNearer || tieNearerPreference) {
                val a = points[i]
                val b = points[i + 1]
                best = Projection(
                    alongM = along,
                    offsetM = off,
                    point = LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t),
                    bearingDeg = bearing,
                )
            }
        }
        return best
    }

    companion object {
        const val HEADING_TOLERANCE_DEG = 100.0

        /**
         * A cleaner line from a recorded ride: Douglas-Peucker with a tolerance in meters.
         * A 1 Hz recording has thousands of points; this keeps the shape and drops the jitter.
         */
        fun simplify(points: List<LatLon>, toleranceM: Double = 6.0): List<LatLon> {
            if (points.size <= 2) return points
            val keep = BooleanArray(points.size)
            keep[0] = true
            keep[points.size - 1] = true
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.addLast(0 to points.size - 1)
            while (stack.isNotEmpty()) {
                val (s, e) = stack.removeLast()
                if (e - s < 2) continue
                var worst = -1
                var worstD = toleranceM
                val (bx, by) = Geo.localXY(points[s], points[e])
                val len2 = bx * bx + by * by
                for (i in s + 1 until e) {
                    val (px, py) = Geo.localXY(points[s], points[i])
                    val t = if (len2 <= 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
                    val d = hypot(px - bx * t, py - by * t)
                    if (d > worstD) { worstD = d; worst = i }
                }
                if (worst >= 0) {
                    keep[worst] = true
                    stack.addLast(s to worst)
                    stack.addLast(worst to e)
                }
            }
            return points.filterIndexed { i, _ -> keep[i] }
        }

        /** The line of a recorded ride: good fixes only, simplified. Null when the ride is too short to follow. */
        fun fromFixes(fixes: List<Fix>, maxAccuracyM: Float = RideStats.MAX_ACCURACY_FOR_DISTANCE_M): RouteLine? {
            val pts = simplify(fixes.filter { it.accuracyM <= maxAccuracyM }.map { LatLon(it.lat, it.lon) })
            val distinct = pts.fold(mutableListOf<LatLon>()) { acc, p -> if (acc.lastOrNull() != p) acc += p; acc }
            if (distinct.size < 2) return null
            val line = RouteLine(distinct)
            return if (line.lengthM < 100.0) null else line
        }
    }
}
