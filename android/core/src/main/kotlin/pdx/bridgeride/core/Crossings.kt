package pdx.bridgeride.core

/**
 * One bridge crossing found in a track. A bridge is a crossing with a direction, not a point:
 * the rider was on the land at one end and then on the land at the other.
 */
data class Crossing(
    val bridgeId: String,
    val bridgeName: String,
    /** The end the rider started from and the end they reached: west, east, north or south. */
    val from: String,
    val to: String,
    /** Indexes into the track: last point on the near bank, first point on the far bank. */
    val startIndex: Int,
    val endIndex: Int,
) {
    /** "westbound" when the rider ended on the west side, and so on. */
    val direction: String get() = "${to}bound"
}

object Crossings {
    /** Points of a track farther apart than this get checked in between, so a sparse line cannot step over a bank. */
    private const val SAMPLE_M = 15.0
    /** GPS error allowed at the water's edge: this far out over the water still counts as the bank. */
    private const val BANK_SLACK_M = 25.0

    /**
     * Finds crossings of bikeable bridges in a track, in the order ridden.
     *
     * Each bridge is the straight line between its two ends, with the water somewhere in the middle
     * (BridgeEnd.bank says where, across the width of the corridor). A crossing is: on the land side
     * of one bank, then on the land side of the other, having stayed near the bridge in between.
     *
     * Why not "passed both ends": the ends are where the bridge meets the street grid, often far
     * from the river. Riders join by ramps near the bank (the Esplanade ramp onto the Hawthorne),
     * and a ride along an approach that drops to the riverbank is not a crossing.
     * Why "stayed near": a ride down one bank, over another bridge and back up the far bank reaches
     * both banks too, but it leaves this bridge's corridor on the way.
     * Nothing here depends on time or distance ridden, so waiting out a bridge lift does not lose
     * the crossing.
     */
    fun detect(track: List<LatLon>, ends: List<BridgeEnd>): List<Crossing> {
        if (track.size < 2) return emptyList()
        val found = mutableListOf<Crossing>()
        val bridges = ends.filter { it.access != BikeAccess.NO }.groupBy { it.id }.values.filter { it.size == 2 }
        for (pair in bridges) {
            val a = pair[0]
            val b = pair[1]
            val pa = LatLon(a.lat, a.lon)
            val deck = RouteLine(listOf(pa, LatLon(b.lat, b.lon)))
            val length = deck.lengthM
            val corridor = corridorM(length)
            val (bx, by) = Geo.localXY(pa, LatLon(b.lat, b.lon))

            var lastOnA = -1
            var lastOnB = -1
            fun visit(p: LatLon, lowIndex: Int, highIndex: Int) {
                val pr = deck.project(p)
                if (pr.offsetM > corridor) { lastOnA = -1; lastOnB = -1; return } // left the bridge
                // Which side of the bridge's line, in corridors: negative is left facing from A to B.
                val (px, py) = Geo.localXY(pa, p)
                val lateral = ((px * by - py * bx) / length) / corridor
                val bankA = a.bankAt(lateral)
                val bankB = b.bankAt(lateral)
                val slack = minOf(BANK_SLACK_M, (1.0 - bankA - bankB).coerceAtLeast(0.0) * length / 4)
                when {
                    pr.alongM <= bankA * length + slack -> { // on A's bank
                        if (lastOnB >= 0) found += Crossing(a.id, a.name, from = b.end, to = a.end, startIndex = lastOnB, endIndex = highIndex)
                        lastOnB = -1
                        lastOnA = lowIndex
                    }
                    pr.alongM >= (1.0 - bankB) * length - slack -> { // on B's bank
                        if (lastOnA >= 0) found += Crossing(a.id, a.name, from = a.end, to = b.end, startIndex = lastOnA, endIndex = highIndex)
                        lastOnA = -1
                        lastOnB = lowIndex
                    }
                    // else: over the water, keep going
                }
            }
            for (i in track.indices) {
                if (i > 0) {
                    val gap = Geo.distanceM(track[i - 1], track[i])
                    val extra = (gap / SAMPLE_M).toInt()
                    for (k in 1..extra) {
                        val t = k / (extra + 1.0)
                        val mid = LatLon(track[i - 1].lat + (track[i].lat - track[i - 1].lat) * t, track[i - 1].lon + (track[i].lon - track[i - 1].lon) * t)
                        visit(mid, i - 1, i)
                    }
                }
                visit(track[i], i, i)
            }
        }
        return found.sortedBy { it.startIndex }
    }

    /**
     * Half-width of the corridor for a bridge of this length: 60 m plus a tenth of the length,
     * between 100 and 400 m. Neighboring Willamette bridges are at least 300 m apart at the
     * ends, and the long Columbia bridges, which curve, have no neighbors at all.
     */
    fun corridorM(lengthM: Double): Double = (60.0 + 0.1 * lengthM).coerceIn(100.0, 400.0)
}
