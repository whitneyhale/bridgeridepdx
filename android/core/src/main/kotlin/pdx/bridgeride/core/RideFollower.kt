package pdx.bridgeride.core

import kotlin.math.max
import kotlin.math.min

enum class GpsState { WAITING, GOOD, WEAK, ESTIMATED }

enum class RouteState { NONE, APPROACHING, ON_ROUTE, OFF_ROUTE, ARRIVED }

/** Everything the ride screen draws, rebuilt on every fix and every clock tick. */
data class FollowState(
    val gps: GpsState = GpsState.WAITING,
    val route: RouteState = RouteState.NONE,
    /** Where the bike marker is drawn. Null until the first usable fix. */
    val position: LatLon? = null,
    /** Which way the marker points. Null until the rider has moved or the compass has answered. */
    val headingDeg: Double? = null,
    val alongM: Double = 0.0,
    val remainingM: Double? = null,
    /** Distance from the line, and the compass direction back to it. Set while approaching or off route. */
    val offRouteM: Double = 0.0,
    val backToRouteDeg: Double? = null,
    val lastFixAgeMs: Long = 0,
    val accuracyM: Float? = null,
    val speedMps: Double = 0.0,
    /** Direction of the line where the rider is; steadier than the heading, so the map turns with this. */
    val routeBearingDeg: Double? = null,
)

/** The numbers behind tasks 1.4 to 1.6. Change them here and the tests say what moved. */
object FollowRules {
    /** N-09: no good fix for this long and the marker is drawn as estimated. */
    const val STALE_MS = 5_000L
    /**
     * The same, for a rider who was stopped at the last fix. Android slows GPS updates to one every
     * 10 to 15 s when the phone is still (seen on the S24, 2026-11-12), so a short silence then is
     * not a lost signal. Moving again brings the fixes back to one a second.
     */
    const val STALE_STOPPED_MS = 30_000L
    /** A fix worse than this does not move the marker. It is still recorded. */
    const val MAX_USABLE_ACCURACY_M = 35f
    /** Within this of the line the marker rides on the line. */
    const val SNAP_M = 25.0
    /** Farther than this from the line, for OFF_ROUTE_HOLD_MS, is off route. */
    const val OFF_ROUTE_M = 40.0
    const val OFF_ROUTE_HOLD_MS = 8_000L
    /** How far behind and ahead of the last known spot a fix is matched to the line. */
    const val BACK_WINDOW_M = 30.0
    const val AHEAD_WINDOW_M = 150.0
    /** A fix matched to the very back of that window, and this far from it, is really farther back. */
    const val BACK_EDGE_SLACK_M = 10.0
    /** A match farther ahead than the window, or behind it, must repeat this many fixes in a row. */
    const val JUMP_AHEAD_FIXES = 3
    const val TURN_BACK_FIXES = 5
    /** After a turn back, this much forward progress means the rider is going the right way again. */
    const val REVERSE_EXIT_M = 15.0
    /** During a gap the marker keeps moving along the line at the last speed for this long, then holds. */
    const val COAST_AFTER_MS = 1_500L
    const val COAST_MAX_MS = 10_000L
    const val COAST_MAX_SPEED_MPS = 9.0
    const val COAST_MIN_SPEED_MPS = 1.0
    /** Slower than this, the GPS direction of travel is noise: hold the heading or use the compass. */
    const val MIN_SPEED_FOR_HEADING_MPS = 1.5
    const val HEADING_MIN_MOVE_M = 8.0
    const val COMPASS_FRESH_MS = 3_000L
    const val ARRIVE_M = 30.0
}

/**
 * Turns GPS fixes into a calm bike marker (tasks 1.4, 1.5, 1.6).
 *
 * Call [onFix] for every fix and [tick] about once a second. Times are from one steady clock,
 * not from the fixes, so a wrong phone clock cannot fake a gap.
 * With no line it only places and points the marker. With a line it also tracks progress,
 * coasts through GPS gaps, and says when the rider has left the line for good.
 */
class RideFollower(private val line: RouteLine?) {

    var state: FollowState = FollowState(route = if (line == null) RouteState.NONE else RouteState.APPROACHING)
        private set

    private var lastGood: Fix? = null
    private var lastGoodAtMs = 0L
    private var lastAnyAtMs = 0L
    private var lastAnyAccuracy: Float? = null

    private var speed = 0.0
    private var heading: Double? = null
    private var headingAnchor: LatLon? = null
    private var compass: Double? = null
    private var compassAtMs = 0L

    private var joined = false
    private var arrived = false
    private var progressM = 0.0
    private var lastOffsetM = 0.0
    private var lastNearest: LatLon? = null
    private var offSinceMs: Long? = null
    private var offRoute = false
    private var aheadCount = 0
    private var behindCount = 0
    private var pending = false
    private var reversing = false

    /** Compass direction the phone faces, true north. Only used while the rider is stopped. */
    fun onCompass(deg: Double, nowMs: Long): FollowState {
        compass = Geo.norm(deg)
        compassAtMs = nowMs
        return rebuild(nowMs)
    }

    fun onFix(f: Fix, nowMs: Long): FollowState {
        lastAnyAtMs = nowMs
        lastAnyAccuracy = f.accuracyM
        if (f.accuracyM > FollowRules.MAX_USABLE_ACCURACY_M) return rebuild(nowMs)

        // Commit what the marker showed while coasting, so a late fix cannot pull it backward.
        if (line != null && joined) progressM = max(progressM, displayAlong(nowMs))

        updateSpeedAndHeading(f)
        lastGood = f
        lastGoodAtMs = nowMs
        // The GPS direction of travel, when the rider is moving fast enough for it to mean something.
        val course = f.bearingDeg?.toDouble()?.takeIf { speed >= FollowRules.MIN_SPEED_FOR_HEADING_MPS }
        if (line != null) updateRoute(LatLon(f.lat, f.lon), f.accuracyM, course, nowMs)
        return rebuild(nowMs)
    }

    fun tick(nowMs: Long): FollowState = rebuild(nowMs)

    private fun updateSpeedAndHeading(f: Fix) {
        val here = LatLon(f.lat, f.lon)
        val prev = lastGood
        val derived = prev?.let {
            val dt = (f.timeMs - it.timeMs) / 1000.0
            if (dt in 0.2..10.0) Geo.distanceM(LatLon(it.lat, it.lon), here) / dt else null
        }
        speed = f.speedMps?.toDouble() ?: derived ?: speed

        val bearing = f.bearingDeg?.toDouble()
        if (bearing != null && speed >= FollowRules.MIN_SPEED_FOR_HEADING_MPS) {
            // Ease toward the new direction so one odd fix does not swing the marker.
            heading = heading?.let { Geo.norm(it + 0.6 * Geo.turnDeg(it, bearing)) } ?: Geo.norm(bearing)
            headingAnchor = here
        } else {
            // No trustworthy direction from GPS: use real movement, and ignore jitter under 8 m.
            val anchor = headingAnchor
            if (anchor == null) headingAnchor = here
            else if (Geo.distanceM(anchor, here) >= FollowRules.HEADING_MIN_MOVE_M) {
                heading = Geo.bearingDeg(anchor, here)
                headingAnchor = here
            }
        }
    }

    /**
     * Nearest spot of the line between fromM and toM. A spot where the line runs the rider's way is
     * preferred when it is close enough to ride on; otherwise the plain nearest spot.
     */
    private fun match(line: RouteLine, p: LatLon, course: Double?, fromM: Double, toM: Double): Projection {
        val sameWay = course?.let { line.projectAlongHeading(p, it, fromM, toM, preferNearM = progressM) }
        return sameWay?.takeIf { it.offsetM <= FollowRules.SNAP_M } ?: line.project(p, fromM, toM, preferNearM = progressM)
    }

    private fun updateRoute(p: LatLon, accuracyM: Float, course: Double?, nowMs: Long) {
        val line = line ?: return
        if (!joined) {
            // Prefer the first half, so starting a loop at its start is not read as its finish.
            val early = line.project(p, 0.0, line.lengthM / 2)
            val pr = if (early.offsetM <= FollowRules.SNAP_M) early else line.project(p)
            lastOffsetM = pr.offsetM
            lastNearest = pr.point
            if (pr.offsetM <= FollowRules.SNAP_M) {
                joined = true
                progressM = pr.alongM
            }
            return
        }

        // First choice: the stretch just ahead of where the rider was, on a part of the line that runs the
        // rider's way. It wins even when another pass of the line (the other leg of an out-and-back on the
        // same street, the other arm of a ramp that doubles back) happens to be a little nearer.
        pending = false
        val window = match(line, p, course, progressM - FollowRules.BACK_WINDOW_M, progressM + FollowRules.AHEAD_WINDOW_M)
        // Pinned to the back edge of the window and not close to it: the rider is really farther back.
        val pinnedBack = window.alongM <= progressM - FollowRules.BACK_WINDOW_M + 0.5 && window.offsetM > FollowRules.BACK_EDGE_SLACK_M
        var pr = window
        if (window.offsetM > FollowRules.SNAP_M || pinnedBack) {
            // Not where we expect. A shortcut rejoins farther ahead; a turn back rejoins behind.
            val ahead = match(line, p, course, progressM, line.lengthM)
            val behind = line.project(p, 0.0, progressM - FollowRules.BACK_WINDOW_M, preferNearM = progressM)
            when {
                !pinnedBack && ahead.offsetM <= FollowRules.SNAP_M -> {
                    behindCount = 0
                    if (++aheadCount >= FollowRules.JUMP_AHEAD_FIXES) { pr = ahead; aheadCount = 0 } else pr = unmatched(window)
                }
                behind.offsetM <= FollowRules.SNAP_M -> {
                    aheadCount = 0
                    if (++behindCount >= FollowRules.TURN_BACK_FIXES) { pr = behind; behindCount = 0; progressM = behind.alongM; reversing = true } else pr = unmatched(behind)
                }
                else -> { aheadCount = 0; behindCount = 0 }
            }
        } else {
            aheadCount = 0
            behindCount = 0
        }

        lastOffsetM = pr.offsetM
        lastNearest = pr.point
        val farLimit = max(FollowRules.OFF_ROUTE_M, 1.5 * accuracyM)
        when {
            pending -> offSinceMs = null // on the line, just not matched yet: never a warning
            pr.offsetM <= FollowRules.SNAP_M -> {
                offSinceMs = null
                offRoute = false
                if (reversing && pr.alongM <= progressM + FollowRules.REVERSE_EXIT_M) {
                    // Riding the line backward: follow the rider back until they clearly go forward again.
                    progressM = min(progressM, pr.alongM)
                } else {
                    reversing = false
                    progressM = max(progressM, pr.alongM)
                }
                if (progressM >= line.lengthM - FollowRules.ARRIVE_M) arrived = true
            }
            pr.offsetM > farLimit -> {
                val since = offSinceMs ?: nowMs.also { offSinceMs = it }
                if (nowMs - since >= FollowRules.OFF_ROUTE_HOLD_MS) offRoute = true
            }
            else -> offSinceMs = null // between the two limits: keep whatever we said before
        }
    }

    /**
     * The rider is on the line somewhere we do not trust yet (ahead by a shortcut, or behind after a turn
     * back). Until it repeats, draw the marker where the GPS says and keep the progress we had.
     */
    private fun unmatched(candidate: Projection): Projection {
        pending = true
        return candidate
    }

    /** Progress shown on screen: the last matched spot, moved on along the line while fixes are missing. */
    private fun displayAlong(nowMs: Long): Double {
        val line = line ?: return 0.0
        if (!joined || offRoute || arrived || pending || reversing || lastOffsetM > FollowRules.SNAP_M) return progressM
        val age = nowMs - lastGoodAtMs
        if (age <= FollowRules.COAST_AFTER_MS || speed < FollowRules.COAST_MIN_SPEED_MPS) return progressM
        val coastS = min(age, FollowRules.COAST_MAX_MS) / 1000.0
        return min(line.lengthM, progressM + min(speed, FollowRules.COAST_MAX_SPEED_MPS) * coastS)
    }

    private fun rebuild(nowMs: Long): FollowState {
        val good = lastGood
        val goodAge = if (good == null) 0L else nowMs - lastGoodAtMs
        val staleMs = if (speed < FollowRules.COAST_MIN_SPEED_MPS) FollowRules.STALE_STOPPED_MS else FollowRules.STALE_MS
        val gps = when {
            good == null -> if (lastAnyAccuracy != null && nowMs - lastAnyAtMs <= FollowRules.STALE_MS) GpsState.WEAK else GpsState.WAITING
            goodAge <= staleMs -> GpsState.GOOD
            nowMs - lastAnyAtMs <= FollowRules.STALE_MS -> GpsState.WEAK // fixes are arriving, but none good enough
            else -> GpsState.ESTIMATED
        }
        val raw = good?.let { LatLon(it.lat, it.lon) }
        val stopped = speed < FollowRules.MIN_SPEED_FOR_HEADING_MPS
        val compassNow = compass?.takeIf { stopped && nowMs - compassAtMs <= FollowRules.COMPASS_FRESH_MS }
        if (compassNow != null) heading = compassNow

        if (line == null) {
            state = FollowState(
                gps = gps, route = RouteState.NONE, position = raw, headingDeg = heading,
                lastFixAgeMs = goodAge, accuracyM = if (gps == GpsState.WEAK) lastAnyAccuracy else good?.accuracyM, speedMps = speed,
            )
            return state
        }

        val along = displayAlong(nowMs)
        val onLine = joined && !offRoute && !pending && lastOffsetM <= FollowRules.SNAP_M
        val route = when {
            arrived -> RouteState.ARRIVED
            !joined -> RouteState.APPROACHING
            offRoute -> RouteState.OFF_ROUTE
            else -> RouteState.ON_ROUTE
        }
        val routeBearing = if (joined) line.bearingAt(along) else null
        val away = !onLine && raw != null
        state = FollowState(
            gps = gps,
            route = route,
            position = if (onLine) line.pointAt(along) else raw,
            // On the line without a fresh fix, the line itself is the best guess of direction.
            headingDeg = if (onLine && gps != GpsState.GOOD && compassNow == null) routeBearing else heading,
            alongM = along,
            remainingM = line.lengthM - along,
            offRouteM = if (away && !pending) lastOffsetM else 0.0,
            backToRouteDeg = if (away && !pending) lastNearest?.let { Geo.bearingDeg(raw!!, it) } else null,
            lastFixAgeMs = goodAge,
            accuracyM = if (gps == GpsState.WEAK) lastAnyAccuracy else good?.accuracyM,
            speedMps = speed,
            routeBearingDeg = routeBearing,
        )
        return state
    }
}
