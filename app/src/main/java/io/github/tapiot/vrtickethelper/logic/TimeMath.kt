package io.github.tapiot.vrtickethelper.logic

import io.github.tapiot.vrtickethelper.model.MINUTES_PER_DAY
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

val FINLAND: ZoneId = ZoneId.of("Europe/Helsinki")

object TimeMath {

    /** Minutes from midnight of [date] (Finnish time) to [time]; next-day times are above 1440. */
    fun minutesFrom(date: LocalDate, time: OffsetDateTime): Int {
        val local = time.atZoneSameInstant(FINLAND).toLocalDateTime()
        val days = ChronoUnit.DAYS.between(date, local.toLocalDate()).toInt()
        return days * MINUTES_PER_DAY + local.hour * 60 + local.minute
    }

    /** "6:05" style clock time in Finnish time, as vr.fi shows it. */
    fun clock(time: OffsetDateTime): String {
        val local = time.atZoneSameInstant(FINLAND)
        return "${local.hour}:${"%02d".format(Locale.ROOT, local.minute)}"
    }

    fun clockFromMinutes(minutes: Int): String {
        val m = Math.floorMod(minutes, MINUTES_PER_DAY)
        return "${m / 60}:${"%02d".format(Locale.ROOT, m % 60)}"
    }

    /** "6 h 18 min", "45 min", "3 h". */
    fun duration(d: Duration): String {
        val total = d.toMinutes().coerceAtLeast(0)
        val h = total / 60
        val m = total % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun dayMarker(offset: Int): String = when {
        offset > 0 -> "+$offset"
        offset < 0 -> "−${-offset}"
        else -> ""
    }

    fun price(cents: Int): String = "€%d.%02d".format(Locale.ROOT, cents / 100, cents % 100)
}
