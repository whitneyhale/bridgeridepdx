package pdx.bridgeride.core

enum class BikeAccess { YES, LIMITED, NO }

/** One end of a bridge, as shipped in assets/bridge_ends.csv (from data/bridges.json). */
data class BridgeEnd(
    val id: String,
    val name: String,
    val end: String, // west, east, north or south
    val lat: Double,
    val lon: Double,
    val access: BikeAccess,
    /**
     * How far along the way to the other end the water starts, 0 to 1: land before it, river after it.
     * Five values, because riverbanks are not square to the bridge: measured 1 and 0.5 corridors to the
     * left of the bridge's line, on the line, and 0.5 and 1 corridors to the right, facing from the
     * bridge's first-listed end to its second (data/water_spans.json).
     */
    val bank: List<Double>,
) {
    /** The bank at a sideways position, in corridors from the bridge's line (-1 far left to 1 far right). */
    fun bankAt(lateral: Double): Double {
        val x = (lateral.coerceIn(-1.0, 1.0) + 1.0) * 2.0 // 0..4 across the five values
        val i = x.toInt().coerceAtMost(3)
        return bank[i] + (bank[i + 1] - bank[i]) * (x - i)
    }
}

object BridgeEnds {
    /** Parses the CSV, skipping the header and any malformed row. */
    fun parse(lines: List<String>): List<BridgeEnd> = lines.drop(1).mapNotNull(::parseLine)

    fun parseLine(line: String): BridgeEnd? {
        val f = splitCsv(line)
        if (f.size != 7) return null
        val lat = f[3].toDoubleOrNull() ?: return null
        val lon = f[4].toDoubleOrNull() ?: return null
        val access = when (f[5]) {
            "yes" -> BikeAccess.YES
            "limited" -> BikeAccess.LIMITED
            "no" -> BikeAccess.NO
            else -> return null
        }
        val bank = f[6].split('/').map { it.toDoubleOrNull()?.takeIf { v -> v in 0.0..1.0 } ?: return null }
        if (bank.size != 5) return null
        return BridgeEnd(f[0], f[1], f[2], lat, lon, access, bank)
    }

    // Minimal CSV: commas, double-quoted fields, doubled quotes inside quotes.
    private fun splitCsv(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
