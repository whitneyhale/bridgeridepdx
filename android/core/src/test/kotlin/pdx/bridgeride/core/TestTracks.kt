package pdx.bridgeride.core

// Shared test data: real bridge ends (from assets/bridge_ends.csv) and helpers that "ride" a line.
object TestTracks {
    val HAWTHORNE_EAST = LatLon(45.512260, -122.661374)
    val HAWTHORNE_WEST = LatLon(45.514108, -122.674600)
    val STEEL_EAST = LatLon(45.528545, -122.666810)
    val STEEL_WEST = LatLon(45.526903, -122.672369)
    val BURNSIDE_EAST = LatLon(45.523086, -122.662565)
    val BURNSIDE_WEST = LatLon(45.523266, -122.671585)
    val MARQUAM_EAST = LatLon(45.510418, -122.666657)
    val MARQUAM_WEST = LatLon(45.503352, -122.674576)
    val BLUMENAUER_NORTH = LatLon(45.527279, -122.658633)
    val BLUMENAUER_SOUTH = LatLon(45.526088, -122.658637)

    /** The real file the app ships (tools/export_bridge_ends.py), so tests use the real banks. */
    val ENDS: List<BridgeEnd> = BridgeEnds.parse(java.io.File("../app/src/main/assets/bridge_ends.csv").readLines())

    /** A point on a bridge's straight line, as a fraction of the way from its first end (east or north) to the other. */
    fun onDeck(id: String, fraction: Double): LatLon {
        val (a, b) = ENDS.filter { it.id == id }
        return LatLon(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction)
    }

    /** A track through the given places with a point about every 10 m, like a 1 Hz recording at a slow roll. */
    fun through(vararg places: LatLon, stepM: Double = 10.0): List<LatLon> {
        val out = mutableListOf(places.first())
        for (i in 1 until places.size) {
            val a = places[i - 1]
            val b = places[i]
            val n = maxOf(1, (Geo.distanceM(a, b) / stepM).toInt())
            for (k in 1..n) out += LatLon(a.lat + (b.lat - a.lat) * k / n, a.lon + (b.lon - a.lon) * k / n)
        }
        return out
    }

    /** Hawthorne Bridge deck, east end to west end: about 1050 m, a straight line. */
    val hawthorneWestbound = RouteLine(listOf(HAWTHORNE_EAST, HAWTHORNE_WEST))

    /** A fix on the line at a distance along it, pushed sideways (positive is right of travel). */
    fun fixOn(line: RouteLine, alongM: Double, timeMs: Long, sideM: Double = 0.0, speed: Float? = 5f, accuracy: Float = 5f, bearing: Float? = null): Fix {
        val on = line.pointAt(alongM)
        val dir = line.bearingAt(alongM)
        val p = if (sideM == 0.0) on else Geo.offset(on, dir + 90.0, sideM)
        return Fix(timeMs, p.lat, p.lon, accuracy, speed, bearing ?: dir.toFloat())
    }
}
