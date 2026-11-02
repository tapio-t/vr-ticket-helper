package io.github.tapiot.vrtickethelper.logic

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DateRange {

    const val MAX_DAYS = 31

    sealed interface Check {
        data object Ok : Check
        data class Invalid(val message: String) : Check
    }

    fun check(start: LocalDate, end: LocalDate, today: LocalDate): Check = when {
        start.isBefore(today) -> Check.Invalid("The start date is in the past.")
        end.isBefore(today) -> Check.Invalid("The end date is in the past.")
        end.isBefore(start) -> Check.Invalid("The end date is before the start date.")
        ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS -> Check.Invalid("Choose at most $MAX_DAYS days.")
        else -> Check.Ok
    }

    /** Outward and return date for every day from [start] to [end], inclusive. */
    fun expand(start: LocalDate, end: LocalDate, nights: Int): List<Pair<LocalDate, LocalDate>> {
        require(nights >= 0)
        val out = ArrayList<Pair<LocalDate, LocalDate>>()
        var d = start
        while (!d.isAfter(end)) {
            out += d to d.plusDays(nights.toLong())
            d = d.plusDays(1)
        }
        return out
    }
}
