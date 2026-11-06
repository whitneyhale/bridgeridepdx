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
)

object BridgeEnds {
    /** Parses the CSV, skipping the header and any malformed row. */
    fun parse(lines: List<String>): List<BridgeEnd> = lines.drop(1).mapNotNull(::parseLine)

    fun parseLine(line: String): BridgeEnd? {
        val f = splitCsv(line)
        if (f.size != 6) return null
        val lat = f[3].toDoubleOrNull() ?: return null
        val lon = f[4].toDoubleOrNull() ?: return null
        val access = when (f[5]) {
            "yes" -> BikeAccess.YES
            "limited" -> BikeAccess.LIMITED
            "no" -> BikeAccess.NO
            else -> return null
        }
        return BridgeEnd(f[0], f[1], f[2], lat, lon, access)
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
