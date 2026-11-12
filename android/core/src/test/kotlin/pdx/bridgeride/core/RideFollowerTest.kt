package pdx.bridgeride.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Release 0.3: bike marker with heading (1.4), GPS gaps stay calm (1.5), follow a line and off-route warning (1.6).
// Each test name starts with the task it proves. Time is in ms on one steady clock; one fix per second.
class RideFollowerTest {
    private val line = TestTracks.hawthorneWestbound
    private val speed = 5.0 // m/s, about 11 mph

    /** Rides the line from fromS to toS seconds at 5 m/s, one fix a second, and returns the last state. */
    private fun ride(f: RideFollower, fromS: Int, toS: Int, sideM: Double = 0.0, on: RouteLine = line): FollowState {
        var s = f.state
        for (t in fromS..toS) s = f.onFix(TestTracks.fixOn(on, t * speed, t * 1000L, sideM), t * 1000L)
        return s
    }

    // ---- 1.6 follow a line ----

    @Test fun T16_before_any_fix_it_is_waiting_and_approaching() {
        val s = RideFollower(line).state
        assertEquals(GpsState.WAITING, s.gps)
        assertEquals(RouteState.APPROACHING, s.route)
        assertNull(s.position)
    }

    @Test fun T16_joins_the_line_and_reports_progress() {
        val s = ride(RideFollower(line), 0, 20)
        assertEquals(RouteState.ON_ROUTE, s.route)
        assertEquals(GpsState.GOOD, s.gps)
        assertEquals(100.0, s.alongM, 1.0)
        assertEquals(line.lengthM - 100.0, s.remainingM!!, 1.0)
    }

    @Test fun T16_marker_rides_on_the_line_when_gps_is_a_little_off() {
        // 15 m to the side is ordinary city GPS error. The marker stays on the line.
        val s = ride(RideFollower(line), 0, 20, sideM = 15.0)
        assertEquals(RouteState.ON_ROUTE, s.route)
        assertEquals(0.0, line.project(s.position!!).offsetM, 0.5)
        assertEquals(0.0, s.offRouteM)
    }

    @Test fun T16_far_from_the_line_at_the_start_is_approaching_with_a_way_back() {
        val f = RideFollower(line)
        val start = Geo.offset(TestTracks.HAWTHORNE_EAST, 180.0, 400.0) // 400 m south of the line's start
        val s = f.onFix(Fix(0, start.lat, start.lon, 5f, 0f, null), 0)
        assertEquals(RouteState.APPROACHING, s.route)
        assertTrue(s.offRouteM in 380.0..420.0, "off ${s.offRouteM}")
        val back = assertNotNull(s.backToRouteDeg)
        assertTrue(Geo.turnDeg(0.0, back) in -20.0..20.0, "the line is north of the rider, got $back")
        assertEquals(start, s.position) // drawn where the rider is, not on the line
    }

    @Test fun T16_off_route_only_after_8_s_beyond_40_m() {
        val f = RideFollower(line)
        ride(f, 0, 20)
        // Wrong turn: 60 m off the line, still moving.
        var s = ride(f, 21, 28, sideM = 60.0)
        assertEquals(RouteState.ON_ROUTE, s.route, "7 s off is not yet off route")
        s = ride(f, 29, 29, sideM = 60.0)
        assertEquals(RouteState.OFF_ROUTE, s.route)
        assertEquals(60.0, s.offRouteM, 2.0)
        assertNotNull(s.backToRouteDeg)
        // The marker is where the rider is, not dragged along the line.
        assertEquals(60.0, line.project(s.position!!).offsetM, 2.0)
    }

    @Test fun T16_between_25_and_40_m_never_warns() {
        val f = RideFollower(line)
        ride(f, 0, 20)
        val s = ride(f, 21, 60, sideM = 33.0)
        assertEquals(RouteState.ON_ROUTE, s.route)
    }

    @Test fun T16_a_poor_fix_needs_more_distance_to_count_as_off() {
        // 45 m off with 34 m accuracy: inside one and a half times the error, so not a wrong turn.
        val f = RideFollower(line)
        ride(f, 0, 20)
        var s = f.state
        for (t in 21..40) s = f.onFix(TestTracks.fixOn(line, t * speed, t * 1000L, sideM = 45.0, accuracy = 34f), t * 1000L)
        assertEquals(RouteState.ON_ROUTE, s.route)
    }

    @Test fun T16_off_route_clears_itself_when_the_rider_rejoins() {
        val f = RideFollower(line)
        ride(f, 0, 20)
        assertEquals(RouteState.OFF_ROUTE, ride(f, 21, 35, sideM = 60.0).route)
        val s = ride(f, 36, 38, sideM = 5.0)
        assertEquals(RouteState.ON_ROUTE, s.route)
        assertEquals(0.0, s.offRouteM)
    }

    @Test fun T16_a_loop_start_is_not_read_as_its_finish() {
        // The loop comes back into its own start from the north.
        val north = Geo.offset(TestTracks.HAWTHORNE_EAST, 0.0, 400.0)
        val loop = RouteLine(listOf(TestTracks.HAWTHORNE_EAST, TestTracks.HAWTHORNE_WEST, Geo.offset(TestTracks.HAWTHORNE_WEST, 0.0, 400.0), north, TestTracks.HAWTHORNE_EAST))
        assertTrue(loop.isLoop)
        val f = RideFollower(loop)
        // Standing 20 m north of the start: nearer the last leg than the first.
        val p = Geo.offset(TestTracks.HAWTHORNE_EAST, 0.0, 20.0)
        val s = f.onFix(Fix(0, p.lat, p.lon, 5f, 0f, null), 0)
        assertEquals(RouteState.ON_ROUTE, s.route)
        assertTrue(s.alongM < 50.0, "joined at ${s.alongM}")
    }

    @Test fun T16_arrives_at_the_end() {
        val f = RideFollower(line)
        val s = ride(f, 0, (line.lengthM / speed).toInt())
        assertEquals(RouteState.ARRIVED, s.route)
        assertTrue(s.remainingM!! <= FollowRules.ARRIVE_M)
        // Rolling on past the end does not un-arrive, and the marker still goes where the rider goes.
        val past = Geo.offset(TestTracks.HAWTHORNE_WEST, 280.0, 120.0)
        val after = f.onFix(Fix(300_000, past.lat, past.lon, 5f, 5f, 280f), 300_000)
        assertEquals(RouteState.ARRIVED, after.route)
        assertEquals(past, after.position)
    }

    @Test fun T16_out_and_back_does_not_skip_to_the_return_leg() {
        val outAndBack = RouteLine(listOf(TestTracks.HAWTHORNE_EAST, TestTracks.HAWTHORNE_WEST, TestTracks.HAWTHORNE_EAST))
        val f = RideFollower(outAndBack)
        ride(f, 0, 20, on = outAndBack)
        // Noisy fixes 30 m off the outbound leg: just as near the return leg, which shares the street.
        val s = ride(f, 21, 40, sideM = 30.0, on = outAndBack)
        assertTrue(s.alongM < outAndBack.lengthM / 2, "progress jumped to ${s.alongM}")
    }

    @Test fun T16_a_ramp_that_doubles_back_is_followed_by_direction() {
        // Up a ramp, hairpin, back down beside it 12 m away: the Esplanade ramps at the Steel and Hawthorne bridges.
        val top = Geo.offset(TestTracks.HAWTHORNE_EAST, 315.0, 110.0)
        val bottom = Geo.offset(Geo.offset(top, 45.0, 12.0), 135.0, 200.0)
        val ramp = RouteLine(listOf(TestTracks.HAWTHORNE_EAST, top, Geo.offset(top, 45.0, 12.0), bottom))
        val f = RideFollower(ramp)
        var last = 0.0
        // 8 m of GPS error to alternating sides puts every other fix nearer the wrong arm.
        for (t in 0..(ramp.lengthM / speed).toInt()) {
            val s = f.onFix(TestTracks.fixOn(ramp, t * speed, t * 1000L, sideM = if (t % 2 == 0) 8.0 else -8.0), t * 1000L)
            assertTrue(s.alongM >= last - 0.001, "went backward at $t s: $last to ${s.alongM}")
            assertTrue(kotlin.math.abs(s.alongM - t * speed) <= 30.0, "progress ${s.alongM} at $t s, rider at ${t * speed}")
            last = s.alongM
        }
        assertEquals(RouteState.ARRIVED, f.state.route)
    }

    @Test fun T16_a_shortcut_rejoins_ahead_after_3_fixes() {
        // A U-shaped line: west, a short hop north, then back east. The rider cuts across the open end.
        val corner = Geo.offset(TestTracks.HAWTHORNE_WEST, 0.0, 200.0)
        val u = RouteLine(listOf(TestTracks.HAWTHORNE_EAST, TestTracks.HAWTHORNE_WEST, corner, Geo.offset(TestTracks.HAWTHORNE_EAST, 0.0, 200.0)))
        val f = RideFollower(u)
        ride(f, 0, 20, on = u) // 100 m along the first leg
        val rejoinAlong = u.lengthM - 100.0 // on the far leg, straight across from the rider
        var s = f.state
        for (i in 0..1) {
            s = f.onFix(TestTracks.fixOn(u, rejoinAlong + i * speed, (30 + i) * 1000L), (30 + i) * 1000L)
            assertTrue(s.alongM < 300.0, "one or two fixes on the far leg are not enough, got ${s.alongM}")
        }
        s = f.onFix(TestTracks.fixOn(u, rejoinAlong + 2 * speed, 32_000L), 32_000L)
        assertEquals(RouteState.ON_ROUTE, s.route)
        assertEquals(rejoinAlong + 2 * speed, s.alongM, 2.0)
    }

    @Test fun T16_turning_back_is_accepted_after_5_fixes() {
        val f = RideFollower(line)
        ride(f, 0, 100) // 500 m along
        // The rider turns around and rides back east along the line.
        var s = f.state
        for (t in 101..116) {
            val truth = 500.0 - (t - 100) * speed
            s = f.onFix(TestTracks.fixOn(line, truth, t * 1000L, bearing = (line.bearingAt(truth) + 180).toFloat()), t * 1000L)
            // Progress holds, but the marker is never more than 45 m from the rider and never warns.
            assertEquals(RouteState.ON_ROUTE, s.route)
            assertTrue(Geo.distanceM(s.position!!, line.pointAt(truth)) <= 45.0, "marker ${Geo.distanceM(s.position!!, line.pointAt(truth))} m from the rider at $t s")
        }
        // 80 m back, and it has been certain for 5 fixes: progress follows the rider.
        assertEquals(500.0 - 16 * speed, s.alongM, 1.0)
        assertEquals(0.0, s.offRouteM)
        // Forward again: progress climbs with the rider.
        for (t in 117..130) s = f.onFix(TestTracks.fixOn(line, 420.0 + (t - 116) * speed, t * 1000L), t * 1000L)
        assertEquals(490.0, s.alongM, 1.0)
    }

    // ---- 1.5 GPS gaps stay calm ----

    @Test fun T15_gap_turns_estimated_after_5_s_and_good_again_on_the_next_fix() {
        val f = RideFollower(line)
        ride(f, 0, 20)
        assertEquals(GpsState.GOOD, f.tick(25_000).gps)
        val est = f.tick(25_001)
        assertEquals(GpsState.ESTIMATED, est.gps)
        assertEquals(5_001, est.lastFixAgeMs)
        assertEquals(GpsState.GOOD, ride(f, 30, 30).gps)
    }

    @Test fun T15_marker_coasts_along_the_line_then_holds() {
        val f = RideFollower(line)
        ride(f, 0, 20) // 100 m along at 5 m/s
        // 6 s into the gap: about 30 m farther, still on the line, pointing along it.
        val at6 = f.tick(26_000)
        assertEquals(130.0, at6.alongM, 1.0)
        assertEquals(0.0, line.project(at6.position!!).offsetM, 0.5)
        assertEquals(line.bearingAt(130.0), at6.headingDeg!!, 0.5)
        assertEquals(RouteState.ON_ROUTE, at6.route)
        // After 10 s it stops guessing: 150 m, and it stays there.
        assertEquals(150.0, f.tick(30_000).alongM, 1.0)
        assertEquals(150.0, f.tick(60_000).alongM, 1.0)
    }

    @Test fun T15_a_stopped_rider_does_not_coast() {
        val f = RideFollower(line)
        ride(f, 0, 20)
        f.onFix(TestTracks.fixOn(line, 100.0, 21_000L, speed = 0.2f), 21_000L) // stopped at a light
        assertEquals(100.0, f.tick(40_000).alongM, 1.0)
    }

    @Test fun T15_the_marker_never_jumps_backward_after_a_gap() {
        val f = RideFollower(line)
        ride(f, 0, 20) // 100 m along
        f.tick(30_000) // coasted to 150 m
        // The rider had slowed under the bridge deck: the first fix back is at 135 m.
        val s = f.onFix(TestTracks.fixOn(line, 135.0, 31_000L), 31_000L)
        assertEquals(150.0, s.alongM, 1.0, "hold and let the rider catch up")
        // Riding on, progress continues from the held spot without a step back.
        var last = s.alongM
        for (t in 32..40) {
            val now = f.onFix(TestTracks.fixOn(line, 135.0 + (t - 31) * speed, t * 1000L), t * 1000L)
            assertTrue(now.alongM >= last, "went back from $last to ${now.alongM}")
            last = now.alongM
        }
        assertEquals(180.0, last, 1.0)
    }

    @Test fun T15_weak_fixes_do_not_move_the_marker() {
        val f = RideFollower(line)
        val before = ride(f, 0, 20)
        // Under the Esplanade decks: fixes keep coming but they are 60 m wide and land in the river.
        var s = before
        for (t in 21..30) {
            val wild = Geo.offset(line.pointAt(100.0), 200.0, 150.0)
            s = f.onFix(Fix(t * 1000L, wild.lat, wild.lon, 60f, 1f, 10f), t * 1000L)
        }
        assertEquals(GpsState.WEAK, s.gps)
        assertEquals(60f, s.accuracyM)
        assertEquals(RouteState.ON_ROUTE, s.route)
        assertEquals(0.0, line.project(s.position!!).offsetM, 0.5)
        assertTrue(s.alongM in 100.0..150.5, "along ${s.alongM}")
    }

    @Test fun T15_without_a_line_the_marker_holds_its_spot_in_a_gap() {
        val f = RideFollower(null)
        val before = ride(f, 0, 20)
        val s = f.tick(40_000)
        assertEquals(GpsState.ESTIMATED, s.gps)
        assertEquals(RouteState.NONE, s.route)
        assertEquals(before.position, s.position)
        assertEquals(before.headingDeg, s.headingDeg)
    }

    @Test fun T15_replay_a_ride_with_three_gaps_cut_out() {
        // The whole bridge at 5 m/s with 13 s between fixes, three times. Checked every quarter second.
        val f = RideFollower(line)
        val gaps = listOf(30..41, 80..91, 150..161)
        var lastAlong = 0.0
        var worst = 0.0
        val endS = (line.lengthM / speed).toInt()
        for (q in 0..endS * 4) {
            val nowMs = q * 250L
            val t = q / 4
            val s = if (q % 4 == 0 && gaps.none { t in it }) f.onFix(TestTracks.fixOn(line, t * speed, nowMs), nowMs) else f.tick(nowMs)
            assertTrue(s.alongM >= lastAlong, "went backward at $nowMs ms: $lastAlong to ${s.alongM}")
            lastAlong = s.alongM
            worst = maxOf(worst, kotlin.math.abs(s.alongM - nowMs / 1000.0 * speed))
            assertTrue(s.route == RouteState.ON_ROUTE || s.route == RouteState.ARRIVED, "state ${s.route} at $nowMs ms")
        }
        // Coasting covers 10 of the 13 s, so the marker is never more than 3 s of riding from the truth.
        assertTrue(worst <= 3.0 * speed, "marker was $worst m from the rider")
        assertEquals(RouteState.ARRIVED, f.state.route)
    }

    // ---- 1.4 bike marker with heading ----

    @Test fun T14_heading_follows_the_direction_of_travel() {
        val s = ride(RideFollower(line), 0, 10)
        assertEquals(line.bearingAt(0.0), s.headingDeg!!, 1.0)
    }

    @Test fun T14_heading_holds_when_the_rider_stops() {
        val f = RideFollower(line)
        val moving = ride(f, 0, 10)
        // Stopped: GPS reports a slow crawl with directions all over the place.
        var s = moving
        for (t in 11..25) {
            s = f.onFix(TestTracks.fixOn(line, 50.0, t * 1000L, sideM = (t % 3) - 1.0, speed = 0.3f, bearing = (t * 97 % 360).toFloat()), t * 1000L)
        }
        assertEquals(moving.headingDeg!!, s.headingDeg!!, 0.01)
    }

    @Test fun T14_one_odd_direction_does_not_swing_the_marker() {
        val f = RideFollower(line)
        val before = ride(f, 0, 10).headingDeg!!
        val s = f.onFix(TestTracks.fixOn(line, 55.0, 11_000L, bearing = (before + 90).toFloat()), 11_000L)
        val swing = kotlin.math.abs(Geo.turnDeg(before, s.headingDeg!!))
        assertTrue(swing in 40.0..60.0, "eased by $swing of 90 degrees")
    }

    @Test fun T14_heading_comes_from_movement_when_gps_gives_no_direction() {
        val f = RideFollower(null)
        var s = f.state
        for (t in 0..5) {
            val p = line.pointAt(t * 4.0)
            s = f.onFix(Fix(t * 1000L, p.lat, p.lon, 5f, null, null), t * 1000L)
        }
        assertEquals(line.bearingAt(0.0), s.headingDeg!!, 2.0)
    }

    @Test fun T14_no_heading_until_the_rider_moves_or_the_compass_answers() {
        val f = RideFollower(null)
        val p = line.pointAt(0.0)
        assertNull(f.onFix(Fix(0, p.lat, p.lon, 5f, 0f, null), 0).headingDeg)
        assertEquals(135.0, f.onCompass(135.0, 500).headingDeg)
    }

    @Test fun T14_compass_is_used_only_while_stopped() {
        val f = RideFollower(line)
        val moving = ride(f, 0, 10)
        // Moving: the compass (a phone in a pocket could say anything) is ignored.
        assertEquals(moving.headingDeg!!, f.onCompass(20.0, 10_500).headingDeg!!, 0.01)
        // Stopped: the compass says which way the rider faces.
        f.onFix(TestTracks.fixOn(line, 50.0, 11_000L, speed = 0f), 11_000L)
        assertEquals(20.0, f.onCompass(20.0, 11_200).headingDeg!!, 0.01)
    }

    @Test fun T14_a_stale_compass_reading_is_dropped() {
        val f = RideFollower(null)
        val p = line.pointAt(0.0)
        f.onFix(Fix(0, p.lat, p.lon, 5f, 0f, null), 0)
        f.onCompass(90.0, 100)
        // The heading it set stays, but a new reading is needed to change it.
        assertEquals(90.0, f.tick(10_000).headingDeg)
    }
}
