package io.github.tapiot.vrtickethelper.web

import io.github.tapiot.vrtickethelper.logic.TimeMath
import io.github.tapiot.vrtickethelper.model.Candidate
import io.github.tapiot.vrtickethelper.site.VrSite

/** Compares page text with the trains the user chose. */
object PageCheck {

    /** Problems found on a ticket page for [c]; empty when everything matches. */
    fun ticketProblems(text: String, c: Candidate): List<String> {
        val problems = ArrayList<String>()
        val t = normalise(text)
        val label = "${TimeMath.clock(c.journey.departure)} train"
        if (!containsNumber(t, c.journey.firstTrainNumber)) problems += "train number ${c.journey.firstTrainNumber} not found"
        if (!containsTime(t, TimeMath.clock(c.journey.departure))) problems += "$label: departure time not found"
        if (!containsTime(t, TimeMath.clock(c.journey.arrival))) problems += "$label: arrival time not found"
        if (!VrSite.dateForms(c.bookingDate).any { t.contains(it) }) problems += "$label: date not found"
        return problems
    }

    fun summaryProblems(text: String, out: Candidate, ret: Candidate): List<String> {
        val problems = ticketProblems(text, out) + ticketProblems(text, ret)
        val adults = VrSite.adultCount(normalise(text))
        return when (adults) {
            null -> problems + "could not confirm a single adult passenger"
            1 -> problems
            else -> problems + "$adults passengers on the summary, expected 1 adult"
        }
    }

    private fun normalise(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    private fun containsNumber(text: String, number: String): Boolean =
        number.isNotEmpty() && Regex("(?<![0-9])${Regex.escape(number)}(?![0-9])").containsMatchIn(text)

    /** Matches "6:05" and "06:05", or "6.05" as some pages write it. */
    private fun containsTime(text: String, clock: String): Boolean {
        val (h, m) = clock.split(":")
        return Regex("(?<![0-9])0?$h[:.]$m(?![0-9])").containsMatchIn(text)
    }
}
