package io.github.tapiot.vrtickethelper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tapiot.vrtickethelper.RangeBoard
import io.github.tapiot.vrtickethelper.Screen
import io.github.tapiot.vrtickethelper.logic.DayResult
import io.github.tapiot.vrtickethelper.logic.Ranking
import io.github.tapiot.vrtickethelper.logic.TimeMath
import io.github.tapiot.vrtickethelper.model.Candidate
import io.github.tapiot.vrtickethelper.model.Station
import io.github.tapiot.vrtickethelper.ui.theme.AppColors
import io.github.tapiot.vrtickethelper.ui.theme.MonoStyle
import java.time.LocalDate

@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("←", fontSize = 22.sp, color = AppColors.Text) }
        Column(Modifier.weight(1f)) {
            Heading(title)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
        }
    }
}

private fun routeText(from: Station?, to: Station?) = "${from?.name ?: "?"} → ${to?.name ?: "?"}"

@Composable
fun TrainsScreen(
    s: Screen.Trains,
    from: Station?,
    to: Station?,
    bookingAllowed: Boolean,
    onBack: () -> Unit,
    onOut: (Int) -> Unit,
    onRet: (Int) -> Unit,
    onBook: (Candidate, Candidate) -> Unit,
) {
    val day = s.day
    val out = day.outward[s.outIndex]
    val ret = day.returns[s.retIndex]
    val valid = Ranking.validReturn(out, ret)
    val cheapestOut = day.outward.minOf { it.priceCents }
    val cheapestRet = day.returns.minOf { it.priceCents }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Closest trains", routeText(from, to), onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                Panel(Modifier.fillMaxWidth().padding(vertical = 8.dp), border = AppColors.BlueText, background = AppColors.BlueContainer) {
                    Column(Modifier.padding(14.dp)) {
                        SectionLabel("Your pair")
                        VSpace(6.dp)
                        PairLine("Out", out)
                        PairLine("Back", ret)
                        VSpace(8.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = AppColors.Text)
                            Text(
                                TimeMath.price(out.priceCents + ret.priceCents),
                                style = MonoStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
                                color = AppColors.Text,
                            )
                        }
                    }
                }
            }
            item {
                VSpace(8.dp)
                SectionLabel("Outward · ${day.outDate.format(LongDayFormat)}")
            }
            itemsIndexed(day.outward) { i, c ->
                TrainRow(c, selected = i == s.outIndex, cheapest = c.priceCents == cheapestOut, enabled = true) { onOut(i) }
            }
            item {
                VSpace(12.dp)
                SectionLabel("Return · ${day.returnDate.format(LongDayFormat)}")
            }
            itemsIndexed(day.returns) { i, c ->
                TrainRow(c, selected = i == s.retIndex, cheapest = c.priceCents == cheapestRet, enabled = Ranking.validReturn(out, c)) { onRet(i) }
            }
            item { VSpace(16.dp) }
        }
        Column(Modifier.padding(16.dp)) {
            if (!bookingAllowed) {
                Text(
                    "Booking in the app needs a newer Android System WebView. Update it from the Play Store; comparing still works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Warning,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = { onBook(out, ret) },
                enabled = valid && bookingAllowed,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("CHOOSE SEATS ON VR.FI", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun PairLine(label: String, c: Candidate) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(44.dp), style = MaterialTheme.typography.labelMedium, color = AppColors.Muted)
        Text(
            c.bookingDate.format(DayFormat),
            Modifier.width(76.dp),
            style = MonoStyle.copy(fontSize = 13.sp),
            color = AppColors.Muted,
        )
        Text(TimeMath.clock(c.journey.departure), style = MonoStyle, color = AppColors.Text)
        DayMarker(c.departureDayOffset)
        Text(" → ", style = MonoStyle, color = AppColors.Muted)
        Text(TimeMath.clock(c.journey.arrival), style = MonoStyle, color = AppColors.Text)
        DayMarker(c.arrivalDayOffset)
        Spacer(Modifier.weight(1f))
        Text(TimeMath.price(c.priceCents), style = MonoStyle, color = AppColors.Text)
    }
}

@Composable
fun RangeScreen(
    board: RangeBoard,
    from: Station?,
    to: Station?,
    progress: String?,
    nights: Int,
    onBack: () -> Unit,
    onOpen: (DayResult.Found) -> Unit,
) {
    val totals = board.results.values.filterIsInstance<DayResult.Found>().mapNotNull { d ->
        d.best?.let { d.outDate to it.first.priceCents + it.second.priceCents }
    }.toMap()
    val min = totals.values.minOrNull()
    val max = totals.values.maxOrNull()
    val nightsText = if (nights == 1) "1 night" else "$nights nights"

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Date range", "${routeText(from, to)} · $nightsText", onBack)
        if (board.running) {
            LinearProgressIndicator(
                progress = { board.results.size.toFloat() / board.days.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (progress != null) Text(progress, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            items(board.days, key = { it.first.toString() }) { (outDate, retDate) ->
                val r = board.results[outDate]
                val total = totals[outDate]
                val cheapest = total != null && total == min
                DayCard(outDate, retDate, r, total, cheapest, min, max, onOpen)
            }
            item { VSpace(16.dp) }
        }
    }
}

@Composable
private fun DayCard(
    outDate: LocalDate,
    retDate: LocalDate,
    r: DayResult?,
    total: Int?,
    cheapest: Boolean,
    min: Int?,
    max: Int?,
    onOpen: (DayResult.Found) -> Unit,
) {
    val found = r as? DayResult.Found
    val clickable = found?.best != null
    Panel(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clickable(enabled = clickable, role = Role.Button) { found?.let(onOpen) },
        border = if (cheapest) AppColors.AmberText else AppColors.Outline,
        background = if (cheapest) AppColors.AmberBg else AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${outDate.format(DayFormat)} → ${retDate.format(DayFormat)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (cheapest) AppColors.AmberText else AppColors.Text,
                    )
                    val best = found?.best
                    Text(
                        when {
                            r == null -> "Searching…"
                            r is DayResult.NotOnSale -> "Not on sale"
                            r is DayResult.NoTrains || best == null -> "No suitable trains"
                            else -> "${TimeMath.clock(best.first.journey.departure)}${TimeMath.dayMarker(best.first.departureDayOffset)}–${TimeMath.clock(best.first.journey.arrival)}" +
                                "  ·  ${TimeMath.clock(best.second.journey.departure)}${TimeMath.dayMarker(best.second.departureDayOffset)}–${TimeMath.clock(best.second.journey.arrival)}"
                        },
                        style = if (best != null) MonoStyle.copy(fontSize = 13.sp) else MaterialTheme.typography.bodySmall,
                        color = AppColors.Muted,
                    )
                }
                when {
                    r == null -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    total != null -> {
                        if (cheapest) Text("★ ", color = AppColors.AmberText, fontSize = 16.sp)
                        Text(
                            TimeMath.price(total),
                            style = MonoStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                            color = if (cheapest) AppColors.AmberText else AppColors.Text,
                        )
                    }
                }
            }
            if (total != null && min != null && max != null) {
                VSpace(8.dp)
                PriceBar(total, min, max, cheapest)
            }
        }
    }
}

@Composable
private fun PriceBar(total: Int, min: Int, max: Int, cheapest: Boolean) {
    // Bars start at 35 % so even the cheapest day shows a visible bar.
    val fraction = if (max == min) 1f else 0.35f + 0.65f * (total - min).toFloat() / (max - min)
    Box(Modifier.fillMaxWidth().height(6.dp).background(AppColors.SurfaceHigh, RoundedCornerShape(3.dp))) {
        Box(
            Modifier.fillMaxWidth(fraction).fillMaxHeight()
                .background(if (cheapest) AppColors.AmberText else AppColors.SignalBlue, RoundedCornerShape(3.dp)),
        )
    }
}
