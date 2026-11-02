package io.github.tapiot.vrtickethelper.web

import io.github.tapiot.vrtickethelper.TestData.journey
import io.github.tapiot.vrtickethelper.logic.Ranking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PageCheckTest {

    private val day = LocalDate.of(2026, 11, 10)
    private val back = LocalDate.of(2026, 11, 12)
    private val out = Ranking.place(day, 720, listOf(journey(day, "6:05", "12:23", 4890, train = "27")), false).single()
    private val ret = Ranking.place(back, 1080, listOf(journey(back, "15:00", "21:10", 3990, train = "48")), false).single()

    @Test fun ticketPageMatches() {
        val text = "Outbound: Tue 10.11.\nIC 27\n6:05 Oulu\n12:23 Helsinki\n1 adult"
        assertEquals(emptyList<String>(), PageCheck.ticketProblems(text, out))
    }

    @Test fun leadingZeroAndNbspAccepted() {
        val text = "10.11.2026 IC 27 06:05 – 12:23"
        assertEquals(emptyList<String>(), PageCheck.ticketProblems(text, out))
    }

    @Test fun wrongTrainNumberIsReported() {
        val text = "Tue 10.11. IC 270 6:05 12:23"
        assertEquals(1, PageCheck.ticketProblems(text, out).size)
    }

    @Test fun wrongDateIsReported() {
        val text = "Wed 11.11. IC 27 6:05 12:23"
        assertTrue(PageCheck.ticketProblems(text, out).single().contains("date"))
    }

    @Test fun timesMustNotMatchInsideLongerNumbers() {
        val text = "Tue 10.11. IC 27 16:05 12:23"
        assertEquals(1, PageCheck.ticketProblems(text, out).size)
    }

    @Test fun summaryWithOneAdultMatches() {
        val text = "10.11. IC 27 6:05 12:23 12.11. IC 48 15:00 21:10 Passengers: 1 adult Total €88.80"
        assertEquals(emptyList<String>(), PageCheck.summaryProblems(text, out, ret))
    }

    @Test fun summaryWithTwoAdultsWarns() {
        val text = "10.11. IC 27 6:05 12:23 12.11. IC 48 15:00 21:10 2 adults"
        assertTrue(PageCheck.summaryProblems(text, out, ret).single().contains("2 passengers"))
    }

    @Test fun summaryWithoutPassengerInfoWarns() {
        val text = "10.11. IC 27 6:05 12:23 12.11. IC 48 15:00 21:10"
        assertEquals(1, PageCheck.summaryProblems(text, out, ret).size)
    }
}
