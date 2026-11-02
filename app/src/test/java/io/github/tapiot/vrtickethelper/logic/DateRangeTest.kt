package io.github.tapiot.vrtickethelper.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateRangeTest {

    private val today = LocalDate.of(2026, 11, 2)

    @Test fun expandsInclusiveWithNights() {
        val days = DateRange.expand(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), 2)
        assertEquals(
            listOf(
                LocalDate.of(2026, 11, 10) to LocalDate.of(2026, 11, 12),
                LocalDate.of(2026, 11, 11) to LocalDate.of(2026, 11, 13),
                LocalDate.of(2026, 11, 12) to LocalDate.of(2026, 11, 14),
            ),
            days,
        )
    }

    @Test fun zeroNightsReturnsSameDay() {
        val d = LocalDate.of(2026, 11, 10)
        assertEquals(listOf(d to d), DateRange.expand(d, d, 0))
    }

    @Test fun crossesMonthAndYear() {
        val days = DateRange.expand(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2), 1)
        assertEquals(4, days.size)
        assertEquals(LocalDate.of(2027, 1, 3), days.last().second)
    }

    @Test fun acceptsThirtyOneDays() {
        assertEquals(DateRange.Check.Ok, DateRange.check(today, today.plusDays(30), today))
    }

    @Test fun rejectsThirtyTwoDays() {
        assertTrue(DateRange.check(today, today.plusDays(31), today) is DateRange.Check.Invalid)
    }

    @Test fun rejectsEndBeforeStart() {
        assertTrue(DateRange.check(today.plusDays(5), today.plusDays(4), today) is DateRange.Check.Invalid)
    }

    @Test fun rejectsPastDates() {
        assertTrue(DateRange.check(today.minusDays(1), today.plusDays(4), today) is DateRange.Check.Invalid)
    }

    @Test fun acceptsToday() {
        assertEquals(DateRange.Check.Ok, DateRange.check(today, today, today))
    }
}
