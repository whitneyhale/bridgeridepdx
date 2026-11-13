package pdx.bridgeride.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Release 0.3 (task 1.6): the line a ride follows.
class RouteLineTest {
    private val line = TestTracks.hawthorneWestbound

    @Test fun T16_length_matches_the_bridge() {
        assertTrue(line.lengthM in 1040.0..1060.0, "length ${line.lengthM}")
    }

    @Test fun T16_point_at_start_middle_end() {
        assertEquals(TestTracks.HAWTHORNE_EAST, line.pointAt(0.0))
        assertEquals(TestTracks.HAWTHORNE_WEST, line.pointAt(line.lengthM))
        val mid = line.pointAt(line.lengthM / 2)
        assertEquals(line.lengthM / 2, Geo.distanceM(TestTracks.HAWTHORNE_EAST, mid), 1.0)
        // Past either end stays on the line.
        assertEquals(TestTracks.HAWTHORNE_WEST, line.pointAt(line.lengthM + 500))
    }

    @Test fun T16_bearing_is_west_northwest() {
        assertTrue(line.bearingAt(100.0) in 270.0..290.0, "bearing ${line.bearingAt(100.0)}")
    }

    @Test fun T16_projects_a_point_beside_the_line() {
        val beside = Geo.offset(line.pointAt(400.0), line.bearingAt(400.0) + 90.0, 18.0)
        val pr = line.project(beside)
        assertEquals(400.0, pr.alongM, 1.0)
        assertEquals(18.0, pr.offsetM, 0.5)
        assertEquals(0.0, Geo.distanceM(pr.point, line.pointAt(400.0)), 1.0)
    }

    @Test fun T16_a_point_before_the_start_projects_to_the_start() {
        val before = Geo.offset(TestTracks.HAWTHORNE_EAST, line.bearingAt(0.0) + 180.0, 50.0)
        val pr = line.project(before)
        assertEquals(0.0, pr.alongM, 0.1)
        assertEquals(50.0, pr.offsetM, 0.5)
    }

    @Test fun T16_window_picks_the_right_pass_of_an_out_and_back() {
        // Out over the bridge and straight back: every spot is on the line twice.
        val outAndBack = RouteLine(listOf(TestTracks.HAWTHORNE_EAST, TestTracks.HAWTHORNE_WEST, TestTracks.HAWTHORNE_EAST))
        val spot = line.pointAt(300.0)
        val out = outAndBack.project(spot, 0.0, outAndBack.lengthM / 2)
        val back = outAndBack.project(spot, outAndBack.lengthM / 2, outAndBack.lengthM)
        assertEquals(300.0, out.alongM, 1.0)
        assertEquals(outAndBack.lengthM - 300.0, back.alongM, 1.0)
        // With no window, the earlier pass wins a tie.
        assertEquals(300.0, outAndBack.project(spot).alongM, 1.0)
    }

    @Test fun T16_window_never_returns_a_point_outside_itself() {
        val pr = line.project(line.pointAt(800.0), 100.0, 250.0)
        assertTrue(pr.alongM in 100.0..250.0, "along ${pr.alongM}")
    }

    @Test fun T16_repeated_points_are_dropped() {
        val l = RouteLine(listOf(TestTracks.HAWTHORNE_EAST, TestTracks.HAWTHORNE_EAST, TestTracks.HAWTHORNE_WEST))
        assertEquals(2, l.points.size)
    }

    @Test fun T16_loop_is_recognized() {
        assertTrue(RouteLine(listOf(TestTracks.HAWTHORNE_EAST, TestTracks.HAWTHORNE_WEST, TestTracks.HAWTHORNE_EAST)).isLoop)
        assertTrue(!line.isLoop)
    }

    @Test fun T16_simplify_keeps_the_shape_within_tolerance() {
        // An L-shaped ride with 3 m of sideways jitter on every point.
        val corner = Geo.offset(TestTracks.HAWTHORNE_EAST, 0.0, 600.0)
        val clean = TestTracks.through(TestTracks.HAWTHORNE_EAST, corner, Geo.offset(corner, 90.0, 600.0))
        val noisy = clean.mapIndexed { i, p -> Geo.offset(p, 45.0, if (i % 2 == 0) 3.0 else -3.0) }
        val simple = RouteLine.simplify(noisy, toleranceM = 6.0)
        assertTrue(simple.size < noisy.size / 10, "kept ${simple.size} of ${noisy.size}")
        val simpleLine = RouteLine(simple)
        for (p in clean) assertTrue(simpleLine.project(p).offsetM <= 9.5, "a point is ${simpleLine.project(p).offsetM} m off")
        assertEquals(1200.0, simpleLine.lengthM, 30.0)
    }

    @Test fun T16_line_from_a_recorded_ride_ignores_bad_fixes() {
        val fixes = (0..100).map { TestTracks.fixOn(line, it * 10.0, it * 1000L) }.toMutableList()
        // One wild fix 300 m off with 80 m accuracy.
        val wild = Geo.offset(line.pointAt(500.0), 0.0, 300.0)
        fixes.add(50, Fix(49_500L, wild.lat, wild.lon, 80f, null, null))
        val made = assertNotNull(RouteLine.fromFixes(fixes))
        assertEquals(1000.0, made.lengthM, 5.0)
        assertTrue(made.points.size <= 4, "points ${made.points.size}")
    }

    @Test fun T16_a_ride_too_short_to_follow_gives_no_line() {
        val fixes = (0..5).map { TestTracks.fixOn(line, it * 10.0, it * 1000L) }
        assertNull(RouteLine.fromFixes(fixes))
        assertNull(RouteLine.fromFixes(emptyList()))
    }

    @Test fun units_read_like_a_rider_says_them() {
        assertEquals("500 ft", Units.distance(152.0))
        assertEquals("50 ft", Units.distance(3.0))
        assertEquals("0.4 mi", Units.distance(640.0))
        assertEquals("5.5 mi", Units.distance(8851.0))
        assertEquals("north", Units.compass(359.0))
        assertEquals("southwest", Units.compass(230.0))
    }
}
