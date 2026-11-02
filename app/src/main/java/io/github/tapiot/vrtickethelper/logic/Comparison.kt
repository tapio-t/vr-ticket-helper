package io.github.tapiot.vrtickethelper.logic

import io.github.tapiot.vrtickethelper.model.Candidate
import io.github.tapiot.vrtickethelper.model.Journey
import java.time.LocalDate

sealed interface SearchOutcome {
    data class Ok(val journeys: List<Journey>) : SearchOutcome
    data object NotOnSale : SearchOutcome
    data class Failed(val message: String) : SearchOutcome
}

/** vr.fi needs the user before searching can continue. */
class SiteCheckNeeded : Exception("vr.fi wants a check")

class SearchFailed(message: String) : Exception(message)

fun interface FareSearch {
    suspend fun search(from: String, to: String, date: LocalDate): SearchOutcome
}

data class TripRequest(
    val from: String,
    val to: String,
    val outTargetMinutes: Int,
    val returnTargetMinutes: Int,
    val directOnly: Boolean,
)

sealed interface DayResult {
    val outDate: LocalDate
    val returnDate: LocalDate

    data class Found(
        override val outDate: LocalDate,
        override val returnDate: LocalDate,
        val outward: List<Candidate>,
        val returns: List<Candidate>,
    ) : DayResult {
        val best: Pair<Candidate, Candidate>? = Pairing.best(outward, returns)
    }

    data class NotOnSale(override val outDate: LocalDate, override val returnDate: LocalDate) : DayResult
    data class NoTrains(override val outDate: LocalDate, override val returnDate: LocalDate) : DayResult
}

object Pairing {
    const val LIST_SIZE = 8

    fun best(outward: List<Candidate>, returns: List<Candidate>): Pair<Candidate, Candidate>? {
        for (o in outward) {
            val r = returns.firstOrNull { Ranking.validReturn(o, it) }
            if (r != null) return o to r
        }
        return null
    }

    /** Returns worth showing: those that fit after at least one of the shown outward trains. */
    fun shownReturns(outward: List<Candidate>, returns: List<Candidate>): List<Candidate> =
        returns.filter { r -> outward.any { Ranking.validReturn(it, r) } }.take(LIST_SIZE)
}

class Comparison(private val search: FareSearch, private val today: LocalDate) {

    suspend fun day(req: TripRequest, outDate: LocalDate, returnDate: LocalDate): DayResult {
        val outward = ranked(req.from, req.to, outDate, req.outTargetMinutes, req.directOnly)
            ?: return DayResult.NotOnSale(outDate, returnDate)
        val returns = ranked(req.to, req.from, returnDate, req.returnTargetMinutes, req.directOnly)
            ?: return DayResult.NotOnSale(outDate, returnDate)
        val shownOut = outward.take(Pairing.LIST_SIZE)
        val shownRet = Pairing.shownReturns(shownOut, returns)
        if (shownOut.isEmpty() || shownRet.isEmpty()) return DayResult.NoTrains(outDate, returnDate)
        return DayResult.Found(outDate, returnDate, shownOut, shownRet)
    }

    /** Ranked trains for one direction, or null when the date is not on sale. */
    private suspend fun ranked(from: String, to: String, date: LocalDate, target: Int, directOnly: Boolean): List<Candidate>? {
        val sameDay = when (val r = search.search(from, to, date)) {
            is SearchOutcome.Ok -> Ranking.place(date, target, r.journeys, directOnly)
            SearchOutcome.NotOnSale -> return null
            is SearchOutcome.Failed -> throw SearchFailed(r.message)
        }
        val all = if (Ranking.needsPreviousDay(sameDay) && date.minusDays(1) >= today) {
            when (val p = search.search(from, to, date.minusDays(1))) {
                is SearchOutcome.Ok -> sameDay + Ranking.previousDayOvernight(date, target, p.journeys, directOnly)
                else -> sameDay
            }
        } else sameDay
        return Ranking.sort(all)
    }
}
