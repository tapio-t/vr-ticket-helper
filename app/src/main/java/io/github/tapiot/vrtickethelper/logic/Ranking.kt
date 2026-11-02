package io.github.tapiot.vrtickethelper.logic

import io.github.tapiot.vrtickethelper.model.Candidate
import io.github.tapiot.vrtickethelper.model.Journey
import io.github.tapiot.vrtickethelper.model.MINUTES_PER_DAY
import java.time.LocalDate
import kotlin.math.abs

object Ranking {

    const val MIN_RETURN_GAP_MINUTES = 10L

    private val byCloseness = compareBy<Candidate>({ abs(it.diff) }, { it.priceCents }, { it.departureMinutes })

    /** Places the trains of one search list on [date], keeping list order. */
    fun place(date: LocalDate, target: Int, list: List<Journey>, directOnly: Boolean): List<Candidate> {
        val seen = HashMap<String, Int>()
        return list.mapNotNull { j ->
            val key = matchKey(j)
            val index = seen.getOrDefault(key, 0)
            seen[key] = index + 1
            if (directOnly && j.changes > 0) return@mapNotNull null
            val dep = TimeMath.minutesFrom(date, j.departure)
            val arr = TimeMath.minutesFrom(date, j.arrival)
            Candidate(j, date, date, dep, arr, arr - target, index)
        }
    }

    /** Departure time, arrival time and first train number: what identifies a train in vr.fi's list. */
    fun matchKey(j: Journey): String =
        "${TimeMath.clock(j.departure)}|${TimeMath.clock(j.arrival)}|${j.firstTrainNumber}"

    /** True when every train leaving on the date arrives later than wanted (or there are none). */
    fun needsPreviousDay(placed: List<Candidate>): Boolean =
        placed.filter { it.departureMinutes in 0 until MINUTES_PER_DAY }.all { it.diff > 0 }

    /**
     * Previous-day trains that leave on the previous day and arrive on the travel date,
     * re-expressed relative to [date] (departure minutes become negative).
     */
    fun previousDayOvernight(date: LocalDate, target: Int, previousList: List<Journey>, directOnly: Boolean): List<Candidate> {
        val previous = date.minusDays(1)
        return place(previous, target + MINUTES_PER_DAY, previousList, directOnly)
            .filter { it.departureMinutes in 0 until MINUTES_PER_DAY }
            .filter { it.arrivalMinutes in MINUTES_PER_DAY until 2 * MINUTES_PER_DAY }
            .map {
                it.copy(
                    travelDate = date,
                    bookingDate = previous,
                    departureMinutes = it.departureMinutes - MINUTES_PER_DAY,
                    arrivalMinutes = it.arrivalMinutes - MINUTES_PER_DAY,
                )
            }
    }

    fun sort(candidates: List<Candidate>): List<Candidate> = candidates.sortedWith(byCloseness)

    /** Whether [ret] leaves at least ten minutes after [out] arrives. */
    fun validReturn(out: Candidate, ret: Candidate): Boolean =
        !ret.journey.departure.isBefore(out.journey.arrival.plusMinutes(MIN_RETURN_GAP_MINUTES))
}
