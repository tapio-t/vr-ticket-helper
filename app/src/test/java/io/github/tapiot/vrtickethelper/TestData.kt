package io.github.tapiot.vrtickethelper

import io.github.tapiot.vrtickethelper.model.Journey
import io.github.tapiot.vrtickethelper.model.Leg
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Small helpers for building trains in Finnish winter time (+02:00). */
object TestData {
    val EET: ZoneOffset = ZoneOffset.ofHours(2)

    fun at(date: LocalDate, hhmm: String): OffsetDateTime {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return OffsetDateTime.of(date.atTime(h, m), EET)
    }

    private var counter = 0

    fun journey(
        date: LocalDate,
        dep: String,
        arr: String,
        price: Int,
        arrDayOffset: Long = 0,
        train: String = (100 + counter).toString(),
        changes: Int = 0,
        depDayOffset: Long = 0,
    ): Journey {
        counter++
        val d = at(date.plusDays(depDayOffset), dep)
        val a = at(date.plusDays(arrDayOffset), arr)
        val legs = (0..changes).map { i -> Leg("IC", if (i == 0) train else "9$i", "OL", "HKI", d, a) }
        return Journey("id$counter", d, a, price, legs)
    }

    fun resource(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)) { "missing test resource $name" }.readText()
}
