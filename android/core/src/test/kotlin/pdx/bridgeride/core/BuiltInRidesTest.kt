package pdx.bridgeride.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Release 0.3: the rides shipped in the app (tools/build_builtin_rides.py) really cross the bridges they
// claim, in the claimed direction, and can be followed from start to finish.
class BuiltInRidesTest {
    private val ends = TestTracks.ENDS
    private val rides = File("../app/src/main/assets/rides").listFiles { f -> f.name.endsWith(".csv") }.orEmpty()
        .sortedBy { it.name }
        .map { assertNotNull(BuiltInRide.parse(it.name.removeSuffix(".csv"), it.readLines()), it.name) }

    @Test fun two_rides_ship() {
        assertEquals(listOf("hawthorne-steel", "sellwood-loop"), rides.map { it.id })
        rides.forEach { assertTrue(it.name.isNotBlank() && it.start.isNotBlank() && it.crosses.isNotEmpty(), it.id) }
    }

    @Test fun each_ride_crosses_exactly_the_bridges_in_its_header_in_order() {
        for (ride in rides) {
            val found = Crossings.detect(ride.points, ends).map { "${it.bridgeId} ${it.direction}" }
            assertEquals(ride.crosses, found, ride.id)
        }
    }

    @Test fun each_ride_is_a_loop_of_a_sensible_length() {
        val miles = rides.associate { it.id to RouteLine(it.points).lengthM / 1609.344 }
        rides.forEach { assertTrue(RouteLine(it.points).isLoop, "${it.id} should end where it starts") }
        assertTrue(miles.getValue("hawthorne-steel") in 2.5..3.2, "hawthorne-steel is ${miles["hawthorne-steel"]} mi")
        assertTrue(miles.getValue("sellwood-loop") in 11.0..12.5, "sellwood-loop is ${miles["sellwood-loop"]} mi")
    }

    @Test fun no_point_of_a_ride_is_far_from_the_next() {
        // A gap would show as a long straight line through buildings or water. The longest real one is a
        // 550 m straight of the Springwater trail.
        for (ride in rides) {
            ride.points.zipWithNext().forEach { (a, b) -> assertTrue(Geo.distanceM(a, b) < 700.0, "${ride.id}: ${Geo.distanceM(a, b)} m between points") }
        }
    }

    @Test fun T16_each_ride_can_be_followed_start_to_finish_with_gps_noise() {
        // Ride the whole line at 5 m/s with 8 m of GPS error. Never off route, never backward, arrives.
        for (ride in rides) {
            val line = RouteLine(ride.points)
            val f = RideFollower(line)
            var last = 0.0
            val seconds = (line.lengthM / 5.0).toInt()
            for (t in 0..seconds) {
                val s = f.onFix(TestTracks.fixOn(line, t * 5.0, t * 1000L, sideM = if (t % 2 == 0) 8.0 else -8.0), t * 1000L)
                assertTrue(s.route == RouteState.ON_ROUTE || s.route == RouteState.ARRIVED, "${ride.id}: ${s.route} at $t s, ${s.alongM} m")
                assertTrue(s.alongM >= last - 0.001, "${ride.id}: went backward at $t s")
                last = s.alongM
            }
            assertEquals(RouteState.ARRIVED, f.state.route, ride.id)
        }
    }

    @Test fun each_ride_starts_straight_not_with_a_u_turn() {
        // A start placed on a ramp made the router open with an out-and-back. 200 m in, be about 200 m away.
        for (ride in rides) {
            val line = RouteLine(ride.points)
            assertTrue(Geo.distanceM(line.pointAt(0.0), line.pointAt(200.0)) > 150.0, ride.id)
        }
    }

    @Test fun T16_the_next_bridge_on_each_ride_is_ahead_of_the_start() {
        for (ride in rides) {
            val line = RouteLine(ride.points)
            val first = Crossings.detect(line.points, ends).first()
            assertTrue(line.alongAtIndex(first.startIndex) > 500.0, "${ride.id}: first bridge at ${line.alongAtIndex(first.startIndex)} m")
        }
    }
}
