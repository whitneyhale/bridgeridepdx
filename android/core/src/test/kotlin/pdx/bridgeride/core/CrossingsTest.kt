package pdx.bridgeride.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pdx.bridgeride.core.TestTracks.BLUMENAUER_NORTH
import pdx.bridgeride.core.TestTracks.BLUMENAUER_SOUTH
import pdx.bridgeride.core.TestTracks.BURNSIDE_EAST
import pdx.bridgeride.core.TestTracks.BURNSIDE_WEST
import pdx.bridgeride.core.TestTracks.ENDS
import pdx.bridgeride.core.TestTracks.HAWTHORNE_EAST
import pdx.bridgeride.core.TestTracks.HAWTHORNE_WEST
import pdx.bridgeride.core.TestTracks.MARQUAM_EAST
import pdx.bridgeride.core.TestTracks.MARQUAM_WEST
import pdx.bridgeride.core.TestTracks.STEEL_EAST
import pdx.bridgeride.core.TestTracks.STEEL_WEST
import pdx.bridgeride.core.TestTracks.through

// Release 0.3: a bridge is a crossing with a direction. Feeds the ride summary, the passport and the "next bridge" line.
class CrossingsTest {

    @Test fun hawthorne_east_to_west_is_westbound() {
        val c = Crossings.detect(through(HAWTHORNE_EAST, HAWTHORNE_WEST), ENDS).single()
        assertEquals("hawthorne", c.bridgeId)
        assertEquals("east", c.from)
        assertEquals("west", c.to)
        assertEquals("westbound", c.direction)
    }

    @Test fun hawthorne_west_to_east_is_eastbound() {
        assertEquals("eastbound", Crossings.detect(through(HAWTHORNE_WEST, HAWTHORNE_EAST), ENDS).single().direction)
    }

    @Test fun blumenauer_south_to_north_is_northbound() {
        // A short bridge: the two ends are about 130 m apart.
        val approach = Geo.offset(BLUMENAUER_SOUTH, 180.0, 200.0)
        val c = Crossings.detect(through(approach, BLUMENAUER_SOUTH, BLUMENAUER_NORTH, Geo.offset(BLUMENAUER_NORTH, 0.0, 200.0)), ENDS).single()
        assertEquals("blumenauer", c.bridgeId)
        assertEquals("northbound", c.direction)
    }

    @Test fun ride_13_hawthorne_out_steel_back_finds_both_in_order() {
        val track = through(HAWTHORNE_EAST, HAWTHORNE_WEST, STEEL_WEST, STEEL_EAST, HAWTHORNE_EAST)
        val found = Crossings.detect(track, ENDS)
        assertEquals(listOf("hawthorne westbound", "steel eastbound"), found.map { "${it.bridgeId} ${it.direction}" })
        assertTrue(found[0].endIndex < found[1].startIndex)
    }

    @Test fun passing_both_ends_of_a_bridge_without_crossing_it_does_not_count() {
        // Past the east end of the Burnside, over the Hawthorne, then past the west end of the Burnside.
        val track = through(BURNSIDE_EAST, HAWTHORNE_EAST, HAWTHORNE_WEST, BURNSIDE_WEST)
        assertEquals(listOf("hawthorne"), Crossings.detect(track, ENDS).map { it.bridgeId })
    }

    @Test fun a_bridge_closed_to_bikes_is_never_counted() {
        assertTrue(Crossings.detect(through(MARQUAM_EAST, MARQUAM_WEST), ENDS).isEmpty())
    }

    @Test fun waiting_at_one_end_does_not_count_twice() {
        val linger = List(120) { HAWTHORNE_WEST } // two minutes at the west end
        val track = through(HAWTHORNE_EAST, HAWTHORNE_WEST) + linger + through(HAWTHORNE_WEST, Geo.offset(HAWTHORNE_WEST, 270.0, 300.0))
        assertEquals(1, Crossings.detect(track, ENDS).size)
    }

    @Test fun there_and_back_is_two_crossings() {
        val track = through(HAWTHORNE_EAST, HAWTHORNE_WEST, HAWTHORNE_EAST)
        assertEquals(listOf("westbound", "eastbound"), Crossings.detect(track, ENDS).map { it.direction })
    }

    @Test fun turning_around_over_the_river_is_not_a_crossing() {
        val midRiver = TestTracks.onDeck("hawthorne", 0.7)
        assertTrue(Crossings.detect(through(HAWTHORNE_EAST, midRiver, HAWTHORNE_EAST), ENDS).isEmpty())
    }

    @Test fun joining_by_the_esplanade_ramp_still_counts() {
        // Ride 6 and 13: south on the Eastbank Esplanade, up the ramp at the riverbank, Hawthorne westbound.
        // The rider never goes near the bridge's east end at Grand Ave, 500 m inland.
        val ramp = TestTracks.onDeck("hawthorne", 0.47)
        val esplanadeNorth = Geo.offset(TestTracks.onDeck("hawthorne", 0.5), 0.0, 400.0)
        val c = Crossings.detect(through(esplanadeNorth, TestTracks.onDeck("hawthorne", 0.5), ramp, HAWTHORNE_WEST), ENDS).single()
        assertEquals("hawthorne westbound", "${c.bridgeId} ${c.direction}")
    }

    @Test fun riding_the_approach_and_dropping_to_the_riverbank_is_not_a_crossing() {
        // West along the Hawthorne viaduct from Grand Ave, down the ramp, north on the Esplanade.
        val ramp = TestTracks.onDeck("hawthorne", 0.47)
        val track = through(HAWTHORNE_EAST, ramp, Geo.offset(ramp, 0.0, 600.0))
        assertTrue(Crossings.detect(track, ENDS).isEmpty())
    }

    @Test fun the_floating_walkway_under_the_burnside_is_not_a_crossing() {
        // The Esplanade floats on the river under the Burnside Bridge, 40% of the way from its east end.
        val under = TestTracks.onDeck("burnside", 0.40)
        val track = through(Geo.offset(under, 0.0, 300.0), under, Geo.offset(under, 180.0, 300.0))
        assertTrue(Crossings.detect(track, ENDS).isEmpty())
    }

    @Test fun waiting_out_a_bridge_lift_does_not_lose_the_crossing() {
        // Ten minutes stopped at the gate, the GPS wandering 3 m each second.
        val gate = TestTracks.onDeck("hawthorne", 0.6)
        val wait = List(600) { Geo.offset(gate, (it * 47 % 360).toDouble(), 3.0) }
        val track = through(HAWTHORNE_EAST, gate) + wait + through(gate, HAWTHORNE_WEST)
        assertEquals("westbound", Crossings.detect(track, ENDS).single().direction)
    }

    @Test fun the_steel_bridge_lower_deck_counts_though_it_lands_beside_the_bridge() {
        // The lower deck walkway reaches the west bank 70 m south of the bridge's line, where the bank
        // is 40 m farther out than on the line itself. Points from the walkway's real path.
        val track = through(
            LatLon(45.52746, -122.66610), LatLon(45.52786, -122.66702), LatLon(45.52803, -122.66743),
            LatLon(45.52771, -122.66860), LatLon(45.52725, -122.66961), LatLon(45.52687, -122.67043), LatLon(45.52656, -122.67024),
        )
        assertEquals("steel westbound", Crossings.detect(track, ENDS).single().let { "${it.bridgeId} ${it.direction}" })
        assertEquals("steel eastbound", Crossings.detect(track.reversed(), ENDS).single().let { "${it.bridgeId} ${it.direction}" })
    }

    @Test fun a_line_with_two_far_apart_points_is_checked_in_between() {
        // A simplified line can have one point each side of the river and nothing over it.
        val c = Crossings.detect(listOf(HAWTHORNE_EAST, Geo.offset(HAWTHORNE_WEST, 280.0, 150.0)), ENDS).single()
        assertEquals("westbound", c.direction)
        assertEquals(0, c.startIndex)
        assertEquals(1, c.endIndex)
    }

    @Test fun gps_noise_of_20_m_still_finds_the_crossing() {
        val noisy = through(HAWTHORNE_EAST, HAWTHORNE_WEST).mapIndexed { i, p -> Geo.offset(p, (i * 73 % 360).toDouble(), 20.0) }
        assertEquals("westbound", Crossings.detect(noisy, ENDS).single().direction)
    }

    @Test fun an_empty_or_single_point_track_is_fine() {
        assertTrue(Crossings.detect(emptyList(), ENDS).isEmpty())
        assertTrue(Crossings.detect(listOf(HAWTHORNE_EAST), ENDS).isEmpty())
    }

    @Test fun built_in_ride_file_parses() {
        val ride = assertNotNull(
            BuiltInRide.parse(
                "test",
                listOf("# name: Hawthorne out, Steel back", "# start: Rose Quarter", "# note: Loop", "45.5,-122.6", "", "bad line", "45.6,-122.7"),
            ),
        )
        assertEquals("Hawthorne out, Steel back", ride.name)
        assertEquals("Rose Quarter", ride.start)
        assertEquals(2, ride.points.size)
        assertNull(BuiltInRide.parse("empty", listOf("# name: nothing")))
    }

    @Test fun bridge_info_line_parses() {
        val header = "id\tlabel\tname\tyear\ttype\tcategory\taccess\tfacility\tsummary\td1\to1\ts1\td2\to2\ts2"
        val row = "hawthorne\tHawthorne\tHawthorne Bridge\t1910\ttruss, vertical lift\tMixed use bridge\tyes\t10 ft sidewalks\tBusy.\teastbound\tyes\tsouth\twestbound\tyes\tnorth"
        val info = BridgeInfo.parse(listOf(header, row, "broken\trow")).single()
        assertEquals("Hawthorne", info.label)
        assertEquals(BikeAccess.YES, info.access)
        assertEquals("north", info.crossing("westbound")!!.side)
        assertEquals("south", info.crossing("eastbound")!!.side)
        assertNull(info.crossing("northbound"))
    }
}
