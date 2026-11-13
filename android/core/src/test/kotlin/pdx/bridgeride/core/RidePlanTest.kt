package pdx.bridgeride.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Release 0.3: the plan a ride follows, the words on the ride screen, and what is saved beside a ride.
class RidePlanTest {
    private val ends = TestTracks.ENDS
    private val infos = BridgeInfo.parse(File("../app/src/main/assets/bridge_info.tsv").readLines())
    private fun shipped(id: String): RidePlan {
        val ride = assertNotNull(BuiltInRide.parse(id, File("../app/src/main/assets/rides/$id.csv").readLines()))
        return assertNotNull(RidePlan.of(ride, ends, infos))
    }
    private val loop = shipped("hawthorne-steel")

    @Test fun every_bridge_has_info_for_the_sheet() {
        assertEquals(14, infos.size)
        assertEquals(ends.map { it.id }.distinct().sorted(), infos.map { it.id }.sorted())
        infos.forEach { assertTrue(it.label.isNotBlank() && it.name.isNotBlank() && it.facility.isNotBlank(), it.id) }
    }

    @Test fun side_of_the_bridge_is_given_only_when_the_data_backs_it() {
        val by = infos.associateBy { it.id }
        // Keep right: different sides each way.
        assertEquals("north", by.getValue("hawthorne").sideFor("westbound"))
        assertEquals("south", by.getValue("hawthorne").sideFor("eastbound"))
        // One path, and the bridge's own text names the side.
        assertEquals("south", by.getValue("steel").sideFor("eastbound"))
        assertEquals("south", by.getValue("morrison").sideFor("westbound"))
        // Same side both ways with nothing to back it: say nothing.
        assertNull(by.getValue("sellwood").sideFor("eastbound"))
        assertNull(by.getValue("blumenauer").sideFor("northbound"))
        // No bikes: nothing.
        assertNull(by.getValue("marquam").sideFor("eastbound"))
    }

    @Test fun plan_lists_its_bridges_in_order_with_direction_and_side() {
        assertEquals(listOf("Hawthorne westbound north", "Steel eastbound south"), loop.bridges.map { "${it.label} ${it.direction} ${it.side}" })
        assertTrue(loop.bridges[0].startM < loop.bridges[0].endM && loop.bridges[0].endM < loop.bridges[1].startM)
        assertEquals("Loop · 2.6 mi · 2 bridges", loop.summary())
        assertEquals(3, shipped("sellwood-loop").bridges.size)
    }

    @Test fun next_bridge_and_on_bridge_follow_the_rider() {
        val h = loop.bridges[0]
        assertEquals("hawthorne", loop.nextBridge(0.0)!!.bridgeId)
        assertNull(loop.bridgeAt(0.0))
        assertEquals("hawthorne", loop.bridgeAt((h.startM + h.endM) / 2)!!.bridgeId)
        assertEquals("steel", loop.nextBridge(h.endM + 1)!!.bridgeId)
        assertNull(loop.nextBridge(loop.bridges[1].endM + 1))
    }

    @Test fun a_plan_needs_a_real_line() {
        assertNull(RidePlan.of("x", "X", "", "", listOf(LatLon(45.5, -122.6)), ends, infos))
    }

    // ---- the banner ----

    private fun state(route: RouteState, along: Double = 0.0, off: Double = 0.0, back: Double? = null) = FollowState(
        gps = GpsState.GOOD, route = route, position = LatLon(45.5, -122.6), alongM = along,
        remainingM = loop.lengthM - along, offRouteM = off, backToRouteDeg = back,
    )

    @Test fun banner_counts_down_to_the_next_bridge() {
        val h = loop.bridges[0]
        val b = Banner.of(loop, state(RouteState.ON_ROUTE, along = h.startM - 152.4), 0.0)
        assertEquals(Banner("500 ft", "Hawthorne Bridge", "Westbound, north side", false, "hawthorne"), b)
    }

    @Test fun banner_says_when_the_rider_is_on_the_bridge() {
        val h = loop.bridges[0]
        val b = Banner.of(loop, state(RouteState.ON_ROUTE, along = (h.startM + h.endM) / 2), 0.0)
        assertEquals("On it", b.big)
        assertEquals("Hawthorne Bridge", b.title)
    }

    @Test fun banner_after_the_last_bridge_counts_down_to_the_finish() {
        val b = Banner.of(loop, state(RouteState.ON_ROUTE, along = loop.bridges[1].endM + 5), 0.0)
        assertEquals("to the finish", b.title)
        assertNull(b.bridgeId)
    }

    @Test fun banner_off_the_line_is_one_calm_warning_with_the_way_back() {
        val b = Banner.of(loop, state(RouteState.OFF_ROUTE, off = 61.0, back = 268.0), 0.0)
        assertEquals(Banner("200 ft", "off the line", "It is west of you. No rerouting yet.", true, null), b)
    }

    @Test fun banner_before_reaching_the_line_points_to_it_without_warning() {
        val b = Banner.of(loop, state(RouteState.APPROACHING, off = 1300.0, back = 180.0), 0.0)
        assertEquals(Banner("0.8 mi", "to the line", "It is south of you.", false, null), b)
    }

    @Test fun banner_with_no_plan_shows_miles_recorded() {
        assertEquals("1.0 mi", Banner.of(null, FollowState(), 1609.344).big)
        assertEquals("Waiting", Banner.of(loop, FollowState(route = RouteState.APPROACHING), 0.0).big)
        assertEquals("Done", Banner.of(loop, state(RouteState.ARRIVED, along = loop.lengthM), 0.0).big)
    }

    // ---- what is saved beside a ride ----

    @Test fun meta_round_trips() {
        val m = RideMeta("Hawthorne out, Steel back", "hawthorne-steel", "Good", listOf("Safer", "Prettier"), listOf("hawthorne westbound", "steel eastbound"))
        assertEquals(m, RideMeta.decode(m.encode()))
    }

    @Test fun meta_tells_no_crossings_from_not_worked_out_yet() {
        assertNull(RideMeta.decode("name=x\n").crossings)
        assertEquals(emptyList(), RideMeta.decode(RideMeta(crossings = emptyList()).encode()).crossings)
        assertEquals(RideMeta(), RideMeta.decode(""))
        assertEquals(RideMeta(), RideMeta.decode("garbage line\n=novalue\n"))
    }

    @Test fun moving_time_leaves_out_stops_and_gaps() {
        val line = TestTracks.hawthorneWestbound
        val fixes = mutableListOf<Fix>()
        for (t in 0..60) fixes += TestTracks.fixOn(line, t * 5.0, t * 1000L)            // 60 s moving
        for (t in 61..120) fixes += TestTracks.fixOn(line, 300.0, t * 1000L, speed = 0f) // 60 s stopped
        for (t in 0..30) fixes += TestTracks.fixOn(line, 300.0 + t * 5.0, (180 + t) * 1000L) // a 60 s gap, then 30 s moving
        assertEquals(90_000L, MovingTime.ms(fixes))
        assertEquals(0L, MovingTime.ms(emptyList()))
    }
}
