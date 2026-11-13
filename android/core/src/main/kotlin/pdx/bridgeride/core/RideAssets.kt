package pdx.bridgeride.core

/**
 * A ride that ships inside the app (assets/rides/NAME.csv, built by tools/build_builtin_rides.py).
 * Header lines are "# key: value"; every other line is "lat,lon".
 */
data class BuiltInRide(
    val id: String,
    val name: String,
    val start: String,
    val note: String,
    /** The crossings the ride was built to make, in order, as "bridge-id direction". Checked by a unit test. */
    val crosses: List<String>,
    val points: List<LatLon>,
) {
    companion object {
        fun parse(id: String, lines: List<String>): BuiltInRide? {
            val meta = mutableMapOf<String, String>()
            val pts = mutableListOf<LatLon>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    val body = line.removePrefix("#").trim()
                    val cut = body.indexOf(':')
                    if (cut > 0) meta[body.substring(0, cut).trim()] = body.substring(cut + 1).trim()
                    continue
                }
                val p = line.split(',')
                if (p.size != 2) continue
                val lat = p[0].toDoubleOrNull() ?: continue
                val lon = p[1].toDoubleOrNull() ?: continue
                pts += LatLon(lat, lon)
            }
            if (pts.size < 2) return null
            val crosses = meta["crosses"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return BuiltInRide(id, meta["name"] ?: id, meta["start"] ?: "", meta["note"] ?: "", crosses, pts)
        }
    }
}

/** Which side of the bridge a rider uses in one direction (rule E-01: keep right). */
data class CrossingInfo(val direction: String, val offered: Boolean, val side: String)

/**
 * What the bridge sheet shows, one line per bridge in assets/bridge_info.tsv
 * (built by tools/export_bridge_info.py from data/bridges.json and data/descriptions.json).
 * Columns: id, label, name, year, type, category, access, facility, summary, then direction/offered/side twice.
 */
data class BridgeInfo(
    val id: String,
    val label: String,
    val name: String,
    val year: String,
    val type: String,
    val category: String,
    val access: BikeAccess,
    val facility: String,
    val summary: String,
    val crossings: List<CrossingInfo>,
) {
    fun crossing(direction: String): CrossingInfo? = crossings.firstOrNull { it.direction == direction }

    /**
     * The side to ride on in one direction, only when the data can be trusted for it:
     * the two directions use different sides (keep right), or the bridge's own text names that
     * side as the one path. Bridges where the data lists the same side both ways with nothing
     * to back it (Sellwood, Interstate, Glenn Jackson, Blumenauer) give null: say nothing rather
     * than send a rider to the wrong side.
     */
    fun sideFor(direction: String): String? {
        val c = crossing(direction)?.takeIf { it.offered && it.side.isNotEmpty() } ?: return null
        val other = crossings.firstOrNull { it.direction != direction }
        val differs = other != null && other.side.isNotEmpty() && other.side != c.side
        val named = "${c.side} side" in "$facility $summary".lowercase()
        return if (differs || named) c.side else null
    }

    companion object {
        const val COLUMNS = 15

        fun parse(lines: List<String>): List<BridgeInfo> = lines.drop(1).mapNotNull(::parseLine)

        fun parseLine(line: String): BridgeInfo? {
            val f = line.split('\t')
            if (f.size != COLUMNS) return null
            val access = when (f[6]) {
                "yes" -> BikeAccess.YES
                "limited" -> BikeAccess.LIMITED
                "no" -> BikeAccess.NO
                else -> return null
            }
            val crossings = listOf(9, 12).mapNotNull { i ->
                if (f[i].isEmpty()) null else CrossingInfo(f[i], f[i + 1] == "yes", f[i + 2])
            }
            return BridgeInfo(f[0], f[1], f[2], f[3], f[4], f[5], access, f[7], f[8], crossings)
        }
    }
}

/** Words for distances the way a rider reads them: feet when close, miles otherwise. */
object Units {
    fun distance(meters: Double): String {
        val feet = meters * 3.28084
        return when {
            feet < 1000 -> "${(Math.round(feet / 50.0) * 50).coerceAtLeast(50)} ft"
            else -> String.format(java.util.Locale.US, "%.1f mi", meters / 1609.344)
        }
    }

    fun miles(meters: Double): String = String.format(java.util.Locale.US, "%.1f mi", meters / 1609.344)

    fun mph(metersPerSecond: Double): String = String.format(java.util.Locale.US, "%.0f mph", metersPerSecond * 2.236936)

    /** Eight-point compass word for a bearing: north, northeast, and so on. */
    fun compass(deg: Double): String {
        val names = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
        return names[(Math.round(Geo.norm(deg) / 45.0).toInt()) % 8]
    }
}
