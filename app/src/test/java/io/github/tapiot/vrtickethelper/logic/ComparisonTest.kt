package io.github.tapiot.vrtickethelper.logic

import io.github.tapiot.vrtickethelper.TestData.journey
import io.github.tapiot.vrtickethelper.model.Journey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ComparisonTest {

    private val today = LocalDate.of(2026, 11, 2)
    private val out = LocalDate.of(2026, 11, 10)
    private val back = LocalDate.of(2026, 11, 12)

    private class FakeSearch(val answers: Map<Triple<String, String, LocalDate>, SearchOutcome>) : FareSearch {
        val calls = mutableListOf<Triple<String, String, LocalDate>>()
        override suspend fun search(from: String, to: String, date: LocalDate): SearchOutcome {
            val key = Triple(from, to, date)
            calls += key
            return answers[key] ?: SearchOutcome.Ok(emptyList())
        }
    }

    private fun ok(vararg j: Journey) = SearchOutcome.Ok(j.toList())

    private val req = TripRequest("OL", "HKI", outTargetMinutes = 12 * 60, returnTargetMinutes = 18 * 60, directOnly = false)

    @Test fun picksClosestPairAndSearchesBothDirections() = runBlocking {
        val s = FakeSearch(
            mapOf(
                Triple("OL", "HKI", out) to ok(journey(out, "6:00", "11:50", 5000), journey(out, "8:00", "14:00", 3000)),
                Triple("HKI", "OL", back) to ok(journey(back, "12:00", "17:50", 4000), journey(back, "15:00", "21:00", 2000)),
            ),
        )
        val r = Comparison(s, today).day(req, out, back) as DayResult.Found
        val best = r.best!!
        assertEquals("6:00", TimeMath.clock(best.first.journey.departure))
        assertEquals("12:00", TimeMath.clock(best.second.journey.departure))
        assertEquals(listOf(Triple("OL", "HKI", out), Triple("HKI", "OL", back)), s.calls)
    }

    @Test fun addsPreviousDayNightTrainWhenAllArriveLate() = runBlocking {
        val r7 = req.copy(outTargetMinutes = 7 * 60)
        val prev = out.minusDays(1)
        val s = FakeSearch(
            mapOf(
                Triple("OL", "HKI", out) to ok(journey(out, "6:00", "12:00", 3000)),
                Triple("OL", "HKI", prev) to ok(journey(prev, "22:00", "6:50", 6000, arrDayOffset = 1)),
                Triple("HKI", "OL", back) to ok(journey(back, "12:00", "18:00", 4000)),
            ),
        )
        val r = Comparison(s, today).day(r7, out, back) as DayResult.Found
        val first = r.outward.first()
        assertEquals(prev, first.bookingDate)
        assertEquals(-1, first.departureDayOffset)
        assertEquals(2, r.outward.size)
        assertTrue(Triple("OL", "HKI", prev) in s.calls)
    }

    @Test fun noPreviousDaySearchBeforeToday() = runBlocking {
        val s = FakeSearch(mapOf(Triple("OL", "HKI", today) to ok(journey(today, "6:00", "23:00", 3000))))
        Comparison(s, today).day(req.copy(outTargetMinutes = 60), today, today.plusDays(2))
        assertFalse(Triple("OL", "HKI", today.minusDays(1)) in s.calls)
    }

    @Test fun notOnSale() = runBlocking {
        val s = FakeSearch(mapOf(Triple("OL", "HKI", out) to SearchOutcome.NotOnSale))
        assertTrue(Comparison(s, today).day(req, out, back) is DayResult.NotOnSale)
    }

    @Test fun noTrains() = runBlocking {
        val s = FakeSearch(emptyMap())
        assertTrue(Comparison(s, today).day(req, out, back) is DayResult.NoTrains)
    }

    @Test(expected = SearchFailed::class)
    fun failureIsReported(): Unit = runBlocking {
        val s = FakeSearch(mapOf(Triple("OL", "HKI", out) to SearchOutcome.Failed("x")))
        Comparison(s, today).day(req, out, back)
    }

    @Test fun sameDayReturnRespectsTenMinuteGap() = runBlocking {
        val r = req.copy(outTargetMinutes = 12 * 60, returnTargetMinutes = 12 * 60)
        val s = FakeSearch(
            mapOf(
                Triple("OL", "HKI", out) to ok(journey(out, "6:00", "12:00", 3000)),
                Triple("HKI", "OL", out) to ok(journey(out, "12:05", "18:00", 1000), journey(out, "13:00", "19:00", 2000)),
            ),
        )
        val found = Comparison(s, today).day(r, out, out) as DayResult.Found
        assertEquals("13:00", TimeMath.clock(found.best!!.second.journey.departure))
        assertEquals(1, found.returns.size)
    }

    @Test fun listsAreCappedAtEight() = runBlocking {
        val many = (0 until 12).map { journey(out, "${6 + it}:00", "${10 + it}:00", 1000 + it) }.toTypedArray()
        val backs = (0 until 12).map { journey(back, "${6 + it}:00", "${10 + it}:00", 1000 + it) }.toTypedArray()
        val s = FakeSearch(mapOf(Triple("OL", "HKI", out) to ok(*many), Triple("HKI", "OL", back) to ok(*backs)))
        val r = Comparison(s, today).day(req, out, back) as DayResult.Found
        assertEquals(8, r.outward.size)
        assertEquals(8, r.returns.size)
    }
}
