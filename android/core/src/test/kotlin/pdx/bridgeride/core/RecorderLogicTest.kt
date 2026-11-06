package pdx.bridgeride.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Tests for release 0.1 (task 1.3). Names start with the rule or slice they prove.
class RecorderLogicTest {

    private fun fix(t: Long, lat: Double, lon: Double, acc: Float = 5f) =
        Fix(timeMs = t, lat = lat, lon = lon, accuracyM = acc, speedMps = 4f, bearingDeg = 90f)

    // Hawthorne Bridge deck, west to east, one fix per second.
    private val start = 1_762_387_200_000L // 2025-11-06T00:00:00Z
    private fun track(seconds: Int, gapAt: Int? = null, gapS: Int = 0): List<Fix> {
        val out = mutableListOf<Fix>()
        var t = start
        for (i in 0 until seconds) {
            if (gapAt != null && i == gapAt) t += gapS * 1000L
            out += fix(t, 45.5134, -122.6740 + i * 0.00005)
            t += 1000L
        }
        return out
    }

    @Test fun S1_line_format_round_trips() {
        val f = Fix(start, 45.5134, -122.674, 4.5f, 5.2f, 91.0f)
        assertEquals(f, FixLine.decode(FixLine.encode(f)))
        val noSpeed = Fix(start, 45.5, -122.6, 12f, null, null)
        assertEquals(noSpeed, FixLine.decode(FixLine.encode(noSpeed)))
    }

    @Test fun S1_broken_last_line_is_skipped_not_a_crash() {
        // A crash mid-write can leave half a line at the end of the file.
        val lines = track(3).map(FixLine::encode) + "1762387203000,45.51"
        assertEquals(3, FixLine.decodeAll(lines).size)
        assertNull(FixLine.decode("not,a,fix"))
    }

    @Test fun S1_no_gap_over_10_s_on_a_clean_track() {
        val r = GapReport.of(track(600))
        assertEquals(0, r.gapsOver10s)
        assertEquals(1000L, r.longestGapMs)
    }

    @Test fun S1_counts_a_12_s_gap_and_reports_where() {
        val r = GapReport.of(track(120, gapAt = 60, gapS = 11))
        assertEquals(1, r.gapsOver10s)
        assertEquals(12_000L, r.longestGapMs)
        assertEquals(start + 59_000L, r.longestGapStartMs)
    }

    @Test fun S1_exactly_10_s_is_not_a_gap() {
        assertEquals(0, GapReport.of(track(30, gapAt = 10, gapS = 9)).gapsOver10s)
    }

    @Test fun S1_distance_is_sane() {
        // 0.00005 deg of longitude at 45.5 N is about 3.9 m; 100 steps about 390 m.
        val d = RideStats.distanceM(track(101))
        assertTrue(d in 380.0..400.0, "distance $d")
    }

    @Test fun S1_bad_fixes_do_not_add_distance() {
        // A 100 m GPS jump with 80 m accuracy is not counted (P-rules: reject poor fixes).
        val t = track(10).toMutableList()
        t.add(6, fix(start + 5_500, 45.5144, -122.6738, acc = 80f)) // after the 5 s fix
        val clean = RideStats.distanceM(track(10))
        assertEquals(clean, RideStats.distanceM(t), 0.5)
    }

    @Test fun S1_gpx_is_valid_shape_and_has_every_fix() {
        val gpx = Gpx.write(track(5), name = "Test ride")
        assertTrue(gpx.startsWith("<?xml"))
        assertTrue("<gpx version=\"1.1\"" in gpx)
        assertEquals(5, Regex("<trkpt ").findAll(gpx).count())
        assertTrue("<time>2025-11-06T00:00:00Z</time>" in gpx)
        assertTrue("lat=\"45.5134000\" lon=\"-122.6740000\"" in gpx)
    }

    @Test fun S1_gpx_escapes_the_name() {
        val gpx = Gpx.write(track(2), name = "Hawthorne <out> & \"back\"")
        assertTrue("Hawthorne &lt;out&gt; &amp; &quot;back&quot;" in gpx)
    }

    @Test fun S1_empty_ride_still_writes_a_valid_gpx() {
        val gpx = Gpx.write(emptyList(), name = "Empty")
        assertTrue("<trkseg>" in gpx && "</gpx>" in gpx)
    }
}
