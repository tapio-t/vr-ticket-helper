package io.github.tapiot.vrtickethelper.logic

import io.github.tapiot.vrtickethelper.TestData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class TimeMathTest {

    private val day = LocalDate.of(2026, 11, 10)

    @Test fun minutesSameDay() {
        assertEquals(6 * 60 + 5, TimeMath.minutesFrom(day, TestData.at(day, "6:05")))
    }

    @Test fun minutesNextDayAddsFullDay() {
        assertEquals(1440 + 30, TimeMath.minutesFrom(day, TestData.at(day.plusDays(1), "0:30")))
    }

    @Test fun minutesPreviousDayIsNegative() {
        assertEquals(23 * 60 - 1440, TimeMath.minutesFrom(day, TestData.at(day.minusDays(1), "23:00")))
    }

    @Test fun minutesUseFinnishTimeWhateverTheOffset() {
        val utc = OffsetDateTime.parse("2026-11-10T08:00:00Z")
        assertEquals(10 * 60, TimeMath.minutesFrom(day, utc))
    }

    @Test fun summerTimeOffset() {
        val summer = LocalDate.of(2026, 7, 1)
        assertEquals(12 * 60, TimeMath.minutesFrom(summer, OffsetDateTime.parse("2026-07-01T12:00:00+03:00")))
    }

    @Test fun clockHasNoLeadingZero() {
        assertEquals("6:05", TimeMath.clock(TestData.at(day, "06:05")))
        assertEquals("23:59", TimeMath.clock(TestData.at(day, "23:59")))
    }

    @Test fun durationText() {
        assertEquals("6 h 18 min", TimeMath.duration(Duration.ofMinutes(378)))
        assertEquals("45 min", TimeMath.duration(Duration.ofMinutes(45)))
        assertEquals("3 h", TimeMath.duration(Duration.ofMinutes(180)))
    }

    @Test fun priceText() {
        assertEquals("€48.90", TimeMath.price(4890))
        assertEquals("€0.05", TimeMath.price(5))
    }

    @Test fun dayMarkers() {
        assertEquals("+1", TimeMath.dayMarker(1))
        assertEquals("−1", TimeMath.dayMarker(-1))
        assertEquals("", TimeMath.dayMarker(0))
    }
}
