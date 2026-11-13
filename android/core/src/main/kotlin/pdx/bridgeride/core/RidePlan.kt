package pdx.bridgeride.core

import java.util.Locale

/** One bridge crossing on a planned line: which bridge, which way, and where along the line it starts and ends. */
data class PlanBridge(
    val bridgeId: String,
    val label: String,
    val direction: String,
    /** The side of the bridge to ride on, when the data is sure of it. */
    val side: String?,
    val startM: Double,
    val endM: Double,
)

/**
 * A ride to follow (release 0.3): a line and the bridge crossings found on it.
 * The line comes from a ride shipped in the app or from one the rider recorded.
 * Routing a new line on the phone is release 0.4.
 */
class RidePlan(
    val id: String,
    val name: String,
    val start: String,
    val note: String,
    val line: RouteLine,
    val bridges: List<PlanBridge>,
) {
    val lengthM: Double get() = line.lengthM

    /** The bridge the rider is on at this distance along the line, if any. */
    fun bridgeAt(alongM: Double): PlanBridge? = bridges.firstOrNull { alongM >= it.startM && alongM <= it.endM }

    /** The next bridge still ahead at this distance along the line. */
    fun nextBridge(alongM: Double): PlanBridge? = bridges.firstOrNull { it.startM > alongM }

    /** "Loop · 2.6 mi · 2 bridges" */
    fun summary(): String {
        val miles = String.format(Locale.US, "%.1f mi", lengthM / 1609.344)
        val count = when (bridges.size) { 0 -> "no bridges"; 1 -> "1 bridge"; else -> "${bridges.size} bridges" }
        return listOf(if (line.isLoop) "Loop" else "One way", miles, count).joinToString(" · ")
    }

    companion object {
        fun of(id: String, name: String, start: String, note: String, points: List<LatLon>, ends: List<BridgeEnd>, infos: List<BridgeInfo>): RidePlan? {
            val line = try { RouteLine(points) } catch (e: IllegalArgumentException) { return null }
            val byId = infos.associateBy { it.id }
            val bridges = Crossings.detect(line.points, ends).map { c ->
                val info = byId[c.bridgeId]
                PlanBridge(
                    bridgeId = c.bridgeId,
                    label = info?.label ?: c.bridgeName,
                    direction = c.direction,
                    side = info?.sideFor(c.direction),
                    startM = line.alongAtIndex(c.startIndex),
                    endM = line.alongAtIndex(c.endIndex),
                )
            }
            return RidePlan(id, name, start, note, line, bridges)
        }

        fun of(ride: BuiltInRide, ends: List<BridgeEnd>, infos: List<BridgeInfo>): RidePlan? =
            of(ride.id, ride.name, ride.start, ride.note, ride.points, ends, infos)
    }
}

/** The words at the top of the ride screen. Built here so they can be tested without a phone. */
data class Banner(
    /** The largest text: a distance, or a two-word state. */
    val big: String,
    val title: String,
    val sub: String?,
    /** True when the rider should notice: drawn in the warning color with the warning icon. */
    val warn: Boolean,
    /** Bridge to draw beside the text, if the banner is about one. */
    val bridgeId: String?,
) {
    companion object {
        private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

        private fun how(b: PlanBridge) = cap(b.direction) + (b.side?.let { ", $it side" } ?: "")

        fun of(plan: RidePlan?, s: FollowState, recordedM: Double): Banner {
            if (plan == null) {
                return Banner(Units.miles(recordedM), "Recording", "No line to follow. Ride anywhere.", false, null)
            }
            if (s.position == null) return Banner("Waiting", "for GPS", plan.name, false, null)
            return when (s.route) {
                RouteState.APPROACHING -> Banner(
                    Units.distance(s.offRouteM), "to the line",
                    s.backToRouteDeg?.let { "It is ${Units.compass(it)} of you." }, false, null,
                )
                RouteState.OFF_ROUTE -> Banner(
                    Units.distance(s.offRouteM), "off the line",
                    (s.backToRouteDeg?.let { "It is ${Units.compass(it)} of you. " } ?: "") + "No rerouting yet.", true, null,
                )
                RouteState.ARRIVED -> Banner("Done", "You rode the whole line", "Hold End to save the ride.", false, null)
                else -> {
                    val on = plan.bridgeAt(s.alongM)
                    val next = plan.nextBridge(s.alongM)
                    when {
                        on != null -> Banner("On it", "${on.label} Bridge", how(on), false, on.bridgeId)
                        next != null -> Banner(Units.distance(next.startM - s.alongM), "${next.label} Bridge", how(next), false, next.bridgeId)
                        else -> Banner(Units.distance(s.remainingM ?: 0.0), "to the finish", "No more bridges.", false, null)
                    }
                }
            }
        }
    }
}

/**
 * What is saved beside a recorded ride (ride-START.meta): its name, the plan it followed,
 * the rider's rating, and the crossings found, so the passport does not re-read every ride.
 * One "key=value" per line.
 */
data class RideMeta(
    val name: String? = null,
    val planId: String? = null,
    val rating: String? = null,
    val reasons: List<String> = emptyList(),
    /** "bridge-id direction" for each crossing, in order. Null means not worked out yet. */
    val crossings: List<String>? = null,
) {
    fun encode(): String = buildString {
        fun put(k: String, v: String?) { if (v != null) append(k).append('=').append(v.replace("\n", " ")).append('\n') }
        put("name", name)
        put("plan", planId)
        put("rating", rating)
        if (reasons.isNotEmpty()) put("reasons", reasons.joinToString(","))
        put("crossings", crossings?.joinToString(","))
    }

    companion object {
        fun decode(text: String): RideMeta {
            val map = text.lineSequence().mapNotNull { line ->
                val cut = line.indexOf('=')
                if (cut <= 0) null else line.substring(0, cut) to line.substring(cut + 1)
            }.toMap()
            fun list(k: String) = map[k]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            return RideMeta(
                name = map["name"]?.takeIf { it.isNotBlank() },
                planId = map["plan"]?.takeIf { it.isNotBlank() },
                rating = map["rating"]?.takeIf { it.isNotBlank() },
                reasons = list("reasons").orEmpty(),
                crossings = if ("crossings" in map) list("crossings").orEmpty() else null,
            )
        }

        fun crossingKey(c: Crossing): String = "${c.bridgeId} ${c.direction}"
    }
}
