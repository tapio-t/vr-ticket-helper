package io.github.tapiot.vrtickethelper.site

import io.github.tapiot.vrtickethelper.TestData
import io.github.tapiot.vrtickethelper.logic.Comparison
import io.github.tapiot.vrtickethelper.logic.DayResult
import io.github.tapiot.vrtickethelper.logic.FareSearch
import io.github.tapiot.vrtickethelper.logic.SearchOutcome
import io.github.tapiot.vrtickethelper.logic.TimeMath
import io.github.tapiot.vrtickethelper.logic.TripRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Saved vr.fi answers (November 2026), with option ids replaced. */
class RealSamplesTest {

    private fun search(name: String) =
        (VrSite.parseSearch(TestData.resource("samples/$name.json")) as VrSite.ParsedSearch.Options).journeys

    @Test fun keepsEveryOptionIncludingBusLegs() {
        val out = search("search-HKI-OL-2026-11-03")
        assertEquals(18, out.size)
        val withBus = out.single { TimeMath.clock(it.departure) == "9:20" }
        assertEquals(4260, withBus.priceCents)
        assertEquals(2, withBus.changes)
        assertEquals("IC 43 + Bus 83303B + IC 833", withBus.trainNames)
    }

    @Test fun reverseDirectionParses() {
        val back = search("search-OL-HKI-2026-11-05")
        assertTrue(back.size > 15)
        assertTrue(back.all { it.priceCents in 1000..30000 })
        assertTrue(back.all { it.arrival.isAfter(it.departure) })
    }

    @Test fun overnightArrivalsAreNextDay() {
        val out = search("search-HKI-OL-2026-11-03")
        val night = out.single { TimeMath.clock(it.departure) == "23:40" }
        assertEquals(LocalDate.of(2026, 11, 4), night.arrival.toLocalDate())
        assertEquals("8:08", TimeMath.clock(night.arrival))
    }

    @Test fun stationsSkipRussia() {
        val stations = VrSite.parseStations(TestData.resource("samples/stations.json"))
        assertTrue(stations.size > 190)
        assertTrue(stations.any { it.code == "HKI" && "Helsingfors" in it.altNames })
        assertTrue(stations.none { it.name.contains("Moscow", ignoreCase = true) || it.name.contains("Moskva", ignoreCase = true) })
        assertTrue(stations.any { it.code == "HPA" })
    }

    @Test fun comparisonOnRealData() = runBlocking {
        val out = LocalDate.of(2026, 11, 3)
        val back = LocalDate.of(2026, 11, 5)
        val data = mapOf(
            Triple("HKI", "OL", out) to search("search-HKI-OL-2026-11-03"),
            Triple("OL", "HKI", back) to search("search-OL-HKI-2026-11-05"),
        )
        val fake = FareSearch { f, t, d -> SearchOutcome.Ok(data[Triple(f, t, d)].orEmpty()) }
        val req = TripRequest("HKI", "OL", 12 * 60, 18 * 60, directOnly = false)
        val r = Comparison(fake, LocalDate.of(2026, 11, 2)).day(req, out, back) as DayResult.Found
        val best = r.best!!
        assertEquals("6:20", TimeMath.clock(best.first.journey.departure))
        assertEquals("12:10", TimeMath.clock(best.first.journey.arrival))
        assertTrue(r.outward.any { it.priceCents == 4260 })
        assertEquals(8, r.outward.size)
    }
}
