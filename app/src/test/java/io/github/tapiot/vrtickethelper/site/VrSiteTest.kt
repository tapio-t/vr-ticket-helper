package io.github.tapiot.vrtickethelper.site

import io.github.tapiot.vrtickethelper.TestData
import io.github.tapiot.vrtickethelper.logic.TimeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class VrSiteTest {

    private val day = LocalDate.of(2026, 11, 10)

    @Test fun parsesHandmadeSearch() {
        val r = VrSite.parseSearch(TestData.resource("samples/search-handmade.json")) as VrSite.ParsedSearch.Options
        assertEquals(listOf("opt-1", "opt-2", "opt-3"), r.journeys.map { it.id })
        val first = r.journeys[0]
        assertEquals(4890, first.priceCents)
        assertEquals("IC 27", first.trainNames)
        assertEquals(0, first.changes)
        assertEquals(1, r.journeys[1].changes)
        assertEquals("S 71 + R 9033", r.journeys[1].trainNames)
        assertEquals("6:05", TimeMath.clock(r.journeys[2].arrival))
    }

    @Test fun errorAnswer() {
        val body = """[{"error":{"message":"Something broke","data":{"code":"INTERNAL_SERVER_ERROR"}}}]"""
        val r = VrSite.parseSearch(body)
        assertTrue(r is VrSite.ParsedSearch.Error)
        assertTrue((r as VrSite.ParsedSearch.Error).message.contains("INTERNAL_SERVER_ERROR"))
    }

    @Test fun unreadableAnswer() {
        assertTrue(VrSite.parseSearch("<html>") is VrSite.ParsedSearch.Error)
        assertTrue(VrSite.parseSearch("""{"x":1}""") is VrSite.ParsedSearch.Error)
    }

    @Test fun emptyOptionsAreAnEmptyList() {
        val r = VrSite.parseSearch("""[{"result":{"data":{"status":"OK","options":[]}}}]""")
        assertEquals(VrSite.ParsedSearch.Options(emptyList()), r)
    }

    @Test fun parsesStationsAndSkipsBadOnes() {
        val body = """{"result":{"data":{"stations":{
            "HKI":{"abbreviation":"HKI","name":"Helsinki","names":{"fi":"Helsinki","sv":"Helsingfors"},"type":"station","countryCodeUic":"10"},
            "OL":{"abbreviation":"OL","name":"Oulu","names":{"fi":"Oulu","sv":"Uleåborg"},"type":"station","countryCodeUic":"10"},
            "ÄHT":{"abbreviation":"ÄHT","name":"Ähtäri","type":"station","countryCodeUic":"10"},
            "MVA":{"abbreviation":"MVA","name":"Moskva","type":"station","countryCodeUic":"20"},
            "bad":{"abbreviation":"x<y","name":"Bad","type":"station"},
            "XX":{"abbreviation":"XX","name":"<img src=x>","type":"station"},
            "BUS":{"abbreviation":"BUS","name":"Bus stop","type":"stop"}
        }}}}"""
        val s = VrSite.parseStations(body)
        assertEquals(listOf("HKI", "OL", "ÄHT"), s.map { it.code })
        assertEquals(listOf("Helsingfors"), s.first { it.code == "HKI" }.altNames)
    }

    @Test fun parsesCardsWithNightTraffic() {
        val raw = """[
            {"dep":"6:05","arr":"12:23","numbers":"27","types":"IC","transfers":"","price":"€52.70","disabled":false,"night":false},
            {"dep":"21:40","arr":"6:05","numbers":"266","types":"PYO","transfers":"","price":"€69.90","disabled":false,"night":false},
            {"dep":"0:30","arr":"7:00","numbers":"901","types":"IC","transfers":"","price":"€30.00","disabled":false,"night":true},
            {"dep":"9:00","arr":"15:00","numbers":"29","types":"IC","transfers":"","price":"€40.00","disabled":true,"night":false},
            {"dep":"9:00","arr":"15:00","numbers":"29","types":"IC","transfers":"","price":"free","disabled":false,"night":false}
        ]"""
        val j = VrSite.parseCards(raw, day)
        assertEquals(3, j.size)
        assertEquals(5270, j[0].priceCents)
        assertEquals(day.plusDays(1), j[1].arrival.toLocalDate())
        assertEquals(day.plusDays(1), j[2].departure.toLocalDate())
        assertEquals("PYO 266", j[1].trainNames)
    }

    @Test fun priceText() {
        assertEquals(5270, VrSite.priceCents("€52.70"))
        assertEquals(5270, VrSite.priceCents("€ 52,70"))
        assertNull(VrSite.priceCents("52.70"))
        assertNull(VrSite.priceCents("€52"))
    }

    @Test fun searchPathCarriesTheInput() {
        val p = VrSite.searchPath("OL", "HKI", day)
        assertTrue(p.startsWith("/api/trpc/journey.searchJourney?batch=1&input="))
        val input = URLDecoder.decode(p.substringAfter("input="), StandardCharsets.UTF_8)
        assertTrue(input.contains("\"departureStation\":\"OL\""))
        assertTrue(input.contains("\"departureTime\":\"2026-11-10\""))
        assertTrue(input.contains("\"type\":\"ADULT\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun searchPathRejectsBadCodes() {
        VrSite.searchPath("OL\"", "HKI", day)
    }

    @Test fun outboundUrl() {
        assertEquals(
            "https://www.vr.fi/en/single-ticket-outbound-search-results?from=OL&to=HKI&outboundDate=2026-11-10&returnDate=2026-11-12&passengers%5B0%5D%5Btype%5D=ADULT",
            VrSite.outboundResultsUrl("OL", "HKI", day, LocalDate.of(2026, 11, 12)),
        )
        assertTrue(!VrSite.outboundResultsUrl("OL", "HKI", day, null).contains("returnDate"))
    }

    @Test fun pageKinds() {
        assertEquals(VrSite.PageKind.OUTBOUND_RESULTS, VrSite.pageKind("/en/single-ticket-outbound-search-results"))
        assertEquals(VrSite.PageKind.RETURN_DETAILS, VrSite.pageKind("/en/single-ticket-return-journey-details"))
        assertEquals(VrSite.PageKind.SUMMARY, VrSite.pageKind("/fi/single-ticket-purchase-summary"))
        assertEquals(VrSite.PageKind.OTHER, VrSite.pageKind("/en"))
    }

    @Test fun fetchReply() {
        val r = VrSite.parseFetchReply("""{"state":"done","status":200,"ct":"application/json","body":"[1]"}""")!!
        assertEquals(200, r.status)
        assertTrue(VrSite.looksLikeJson(r))
        assertTrue(!VrSite.looksLikeJson(r.copy(contentType = "text/html", body = "<html>")))
        assertNull(VrSite.parseFetchReply("""{"state":"error","message":"x"}"""))
    }

    @Test fun clickScriptRejectsUnsafeArguments() {
        val ok = VrSite.clickTrainScript("6:05", "12:23", "27", 0)
        assertTrue(ok.contains("location.origin !== \"https://www.vr.fi\""))
        val failures = listOf(
            { VrSite.clickTrainScript("6:05\"", "12:23", "27", 0) },
            { VrSite.clickTrainScript("6:05", "12:23", "27);alert(1", 0) },
            { VrSite.clickTrainScript("6:05", "12:23", "27", 99) },
        )
        failures.forEach { f ->
            try { f(); throw AssertionError("accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun everyScriptChecksTheOrigin() {
        listOf(
            VrSite.PAGE_STATE_SCRIPT, VrSite.CLICK_CHANGE_SEAT_SCRIPT, VrSite.PAGE_TEXT_SCRIPT, VrSite.READ_CARDS_SCRIPT,
            VrSite.fetchStartScript("abcdefgh12", "/api/x"), VrSite.fetchPollScript("abcdefgh12"),
        ).forEach { s ->
            val firstStatement = s.lines().map { it.trim() }.first { it.isNotEmpty() && it != "(function () {" }
            assertEquals("if (location.origin !== \"https://www.vr.fi\") return null;", firstStatement)
        }
    }

    @Test fun adultCount() {
        assertEquals(1, VrSite.adultCount("Passengers 1 adult"))
        assertEquals(2, VrSite.adultCount("2 adults"))
        assertEquals(1, VrSite.adultCount("Adult, Eco"))
        assertNull(VrSite.adultCount("Child"))
    }
}
