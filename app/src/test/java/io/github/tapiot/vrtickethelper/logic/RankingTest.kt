package io.github.tapiot.vrtickethelper.logic

import io.github.tapiot.vrtickethelper.TestData.journey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RankingTest {

    private val day = LocalDate.of(2026, 11, 10)
    private val noon = 12 * 60

    @Test fun sortsByGapThenPrice() {
        val list = listOf(
            journey(day, "6:00", "11:00", 5000),  // -60
            journey(day, "7:00", "12:30", 4000),  // +30
            journey(day, "6:30", "11:30", 3000),  // -30, cheaper than the +30 one
            journey(day, "9:00", "14:00", 1000),  // +120
        )
        val ranked = Ranking.sort(Ranking.place(day, noon, list, directOnly = false))
        assertEquals(listOf(-30, 30, -60, 120), ranked.map { it.diff })
    }

    @Test fun nextDayArrivalCountsFullDay() {
        val j = journey(day, "22:00", "6:00", 3000, arrDayOffset = 1)
        val c = Ranking.place(day, noon, listOf(j), false).single()
        assertEquals(1440 + 360, c.arrivalMinutes)
        assertEquals(1, c.arrivalDayOffset)
        assertEquals(1440 + 360 - noon, c.diff)
    }

    @Test fun noCapOnTheGap() {
        val j = journey(day, "23:00", "23:50", 1000)
        assertEquals(1, Ranking.sort(Ranking.place(day, 6 * 60, listOf(j), false)).size)
    }

    @Test fun directOnlyDropsChanges() {
        val list = listOf(journey(day, "6:00", "11:00", 1000, changes = 1), journey(day, "7:00", "12:00", 2000))
        val placed = Ranking.place(day, noon, list, directOnly = true)
        assertEquals(1, placed.size)
        assertEquals(0, placed.single().journey.changes)
    }

    @Test fun duplicatesAreCountedInListOrder() {
        val a = journey(day, "6:00", "11:00", 1000, train = "27")
        val b = journey(day, "6:00", "11:00", 1200, train = "27")
        val c = journey(day, "7:00", "12:00", 1200, train = "29")
        val placed = Ranking.place(day, noon, listOf(a, b, c), false)
        assertEquals(listOf(0, 1, 0), placed.map { it.duplicateIndex })
    }

    @Test fun duplicateIndexIgnoresTheDirectFilter() {
        val a = journey(day, "6:00", "11:00", 1000, train = "27", changes = 1)
        val b = journey(day, "6:00", "11:00", 1200, train = "27")
        val placed = Ranking.place(day, noon, listOf(a, b), directOnly = true)
        assertEquals(1, placed.single().duplicateIndex)
    }

    @Test fun previousDayNeededWhenEverythingArrivesLate() {
        val list = listOf(journey(day, "6:00", "12:30", 1000), journey(day, "8:00", "14:00", 1000))
        assertTrue(Ranking.needsPreviousDay(Ranking.place(day, 7 * 60, list, false)))
    }

    @Test fun previousDayNotNeededWhenOneArrivesInTime() {
        val list = listOf(journey(day, "5:00", "6:50", 1000), journey(day, "8:00", "14:00", 1000))
        assertFalse(Ranking.needsPreviousDay(Ranking.place(day, 7 * 60, list, false)))
    }

    @Test fun previousDayNotNeededForExactArrival() {
        val list = listOf(journey(day, "5:00", "7:00", 1000))
        assertFalse(Ranking.needsPreviousDay(Ranking.place(day, 7 * 60, list, false)))
    }

    @Test fun nightTrafficDoesNotCountAsLeavingOnTheDate() {
        // Departs after midnight (listed under the date's night traffic) and arrives early next day.
        val list = listOf(
            journey(day, "8:00", "14:00", 1000),
            journey(day, "0:30", "6:00", 1000, depDayOffset = 1, arrDayOffset = 1),
        )
        assertTrue(Ranking.needsPreviousDay(Ranking.place(day, 7 * 60, list, false)))
    }

    @Test fun previousDayOvernightTrainsAreShifted() {
        val prev = day.minusDays(1)
        val list = listOf(
            journey(prev, "21:00", "7:10", 6000, arrDayOffset = 1),   // overnight, arrives on the travel date
            journey(prev, "10:00", "15:00", 3000),                    // arrives on the previous day: ignored
            journey(prev, "23:30", "3:00", 4000, arrDayOffset = 2),   // arrives the day after: ignored
            journey(prev, "0:20", "6:00", 4000, depDayOffset = 1, arrDayOffset = 1), // leaves on the travel date: ignored
        )
        val c = Ranking.previousDayOvernight(day, 7 * 60, list, false).single()
        assertEquals(21 * 60 - 1440, c.departureMinutes)
        assertEquals(7 * 60 + 10, c.arrivalMinutes)
        assertEquals(10, c.diff)
        assertEquals(-1, c.departureDayOffset)
        assertEquals(0, c.arrivalDayOffset)
        assertEquals(prev, c.bookingDate)
        assertEquals(day, c.travelDate)
    }

    @Test fun returnMustLeaveTenMinutesAfterArrival() {
        val out = Ranking.place(day, noon, listOf(journey(day, "8:00", "12:00", 1000)), false).single()
        val tooSoon = Ranking.place(day, noon, listOf(journey(day, "12:09", "16:00", 1000)), false).single()
        val ok = Ranking.place(day, noon, listOf(journey(day, "12:10", "16:00", 1000)), false).single()
        assertFalse(Ranking.validReturn(out, tooSoon))
        assertTrue(Ranking.validReturn(out, ok))
    }
}
