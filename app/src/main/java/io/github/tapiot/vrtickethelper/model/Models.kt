package io.github.tapiot.vrtickethelper.model

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

data class Station(
    val code: String,
    val name: String,
    val altNames: List<String> = emptyList(),
)

data class Leg(
    val trainType: String,
    val trainNumber: String,
    val from: String,
    val to: String,
    val departure: OffsetDateTime,
    val arrival: OffsetDateTime,
)

data class Journey(
    val id: String,
    val departure: OffsetDateTime,
    val arrival: OffsetDateTime,
    val priceCents: Int,
    val legs: List<Leg>,
) {
    val changes: Int get() = (legs.size - 1).coerceAtLeast(0)
    val duration: Duration get() = Duration.between(departure, arrival)
    val firstTrainNumber: String get() = legs.firstOrNull()?.trainNumber.orEmpty()
    val trainNames: String get() = legs.joinToString(" + ") { "${it.trainType} ${it.trainNumber}".trim() }
}

/** A journey placed on a travel date: minutes are counted from midnight of [travelDate]. */
data class Candidate(
    val journey: Journey,
    val travelDate: LocalDate,
    /** The date vr.fi lists this train under; one day earlier for previous-day night trains. */
    val bookingDate: LocalDate,
    val departureMinutes: Int,
    val arrivalMinutes: Int,
    val diff: Int,
    /** Index among trains in the same list with the same departure, arrival and first train number. */
    val duplicateIndex: Int = 0,
) {
    val departureDayOffset: Int get() = Math.floorDiv(departureMinutes, MINUTES_PER_DAY)
    val arrivalDayOffset: Int get() = Math.floorDiv(arrivalMinutes, MINUTES_PER_DAY)
    val priceCents: Int get() = journey.priceCents
}

data class Route(val from: Station, val to: Station)

const val MINUTES_PER_DAY = 1440
