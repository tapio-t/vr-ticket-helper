package io.github.tapiot.vrtickethelper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tapiot.vrtickethelper.SearchForm
import io.github.tapiot.vrtickethelper.UiState
import io.github.tapiot.vrtickethelper.data.RecentRoute
import io.github.tapiot.vrtickethelper.logic.DateRange
import io.github.tapiot.vrtickethelper.today
import io.github.tapiot.vrtickethelper.ui.theme.AppColors
import io.github.tapiot.vrtickethelper.ui.theme.MonoStyle
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class SearchActions(
    val update: ((SearchForm) -> SearchForm) -> Unit,
    val swap: () -> Unit,
    val find: () -> Unit,
    val useRecent: (RecentRoute) -> Unit,
    val openHealth: () -> Unit,
    val clearSiteData: () -> Unit,
    val clearHistory: () -> Unit,
    val setHistory: (Boolean) -> Unit,
    val reloadStations: () -> Unit,
)

private val TimeFormat = DateTimeFormatter.ofPattern("H:mm")

private enum class Picker { DEPART, RETURN, START, END, ARRIVE_TIME, BACK_TIME }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(state: UiState, recent: List<RecentRoute>, historyOn: Boolean, a: SearchActions) {
    val f = state.form
    var picker by remember { mutableStateOf<Picker?>(null) }
    var menu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Heading("VR Ticket Helper", Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Menu" }) {
                    Text("⋮", fontSize = 22.sp, color = AppColors.Text)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Check vr.fi") }, onClick = { menu = false; a.openHealth() })
                    DropdownMenuItem(text = { Text("Reload station list") }, onClick = { menu = false; a.reloadStations() })
                    DropdownMenuItem(text = { Text("Clear vr.fi data") }, onClick = { menu = false; a.clearSiteData() })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Remember recent routes") },
                        trailingIcon = { Checkbox(checked = historyOn, onCheckedChange = null) },
                        onClick = { a.setHistory(!historyOn) },
                    )
                    DropdownMenuItem(text = { Text("Clear history") }, onClick = { menu = false; a.clearHistory() })
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            if (state.stationsLoading && state.stations.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading stations from vr.fi…", style = MaterialTheme.typography.bodySmall, color = AppColors.Muted)
                }
            }
            VSpace(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StationField("From", f.from, state.stations) { s -> a.update { it.copy(from = s) } }
                    VSpace(8.dp)
                    StationField("To", f.to, state.stations) { s -> a.update { it.copy(to = s) } }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedIconButton(onClick = a.swap, modifier = Modifier.semantics { contentDescription = "Swap stations" }) {
                    Text("⇅", fontSize = 20.sp, color = AppColors.BlueText)
                }
            }

            if (recent.isNotEmpty()) {
                VSpace(12.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { r ->
                        AssistChip(onClick = { a.useRecent(r) }, label = { Text("${r.fromName} – ${r.toName}") })
                    }
                }
            }

            VSpace(20.dp)
            SectionLabel("Outward")
            VSpace(6.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField("Departure date", f.departDate.format(LongDayFormat), enabled = !f.rangeMode, Modifier.weight(1f)) { picker = Picker.DEPART }
                PickerField("Arrive there around", f.arriveAround.format(TimeFormat), enabled = true, Modifier.weight(1f)) { picker = Picker.ARRIVE_TIME }
            }
            VSpace(14.dp)
            SectionLabel("Return")
            VSpace(6.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField("Return date", f.returnDate.format(LongDayFormat), enabled = !f.rangeMode, Modifier.weight(1f)) { picker = Picker.RETURN }
                PickerField("Back home around", f.backAround.format(TimeFormat), enabled = true, Modifier.weight(1f)) { picker = Picker.BACK_TIME }
            }

            VSpace(16.dp)
            SwitchRow("Direct trains only", f.directOnly) { v -> a.update { it.copy(directOnly = v) } }
            SwitchRow("Date range comparison", f.rangeMode) { v -> a.update { it.copy(rangeMode = v) } }

            VSpace(4.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField("Start date", f.rangeStart.format(LongDayFormat), enabled = f.rangeMode, Modifier.weight(1f)) { picker = Picker.START }
                PickerField("End date", f.rangeEnd.format(LongDayFormat), enabled = f.rangeMode, Modifier.weight(1f)) { picker = Picker.END }
            }
            VSpace(8.dp)
            NightsStepper(f.nights, f.rangeMode) { n -> a.update { it.copy(nights = n) } }
            if (f.rangeMode) {
                val days = ChronoUnit.DAYS.between(f.rangeStart, f.rangeEnd) + 1
                Text(
                    if (days in 1..DateRange.MAX_DAYS) "$days days to compare" else "Choose 1–${DateRange.MAX_DAYS} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (days in 1..DateRange.MAX_DAYS) AppColors.Muted else AppColors.Error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            VSpace(24.dp)
        }

        Column(Modifier.padding(16.dp)) {
            if (state.progress != null) {
                Text(state.progress, style = MaterialTheme.typography.bodySmall, color = AppColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
            }
            Button(
                onClick = a.find,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.Text)
                else Text("FIND TRAINS", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    val t = today()
    when (picker) {
        Picker.DEPART -> DatePickerPopup(f.departDate, t, { picker = null }) { d ->
            a.update { it.copy(departDate = d, returnDate = if (it.returnDate.isBefore(d)) d else it.returnDate) }
        }
        Picker.RETURN -> DatePickerPopup(f.returnDate, maxOf(t, f.departDate), { picker = null }) { d -> a.update { it.copy(returnDate = d) } }
        Picker.START -> DatePickerPopup(f.rangeStart, t, { picker = null }) { d ->
            a.update { it.copy(rangeStart = d, rangeEnd = if (it.rangeEnd.isBefore(d)) d else it.rangeEnd) }
        }
        Picker.END -> DatePickerPopup(f.rangeEnd, maxOf(t, f.rangeStart), { picker = null }) { d -> a.update { it.copy(rangeEnd = d) } }
        Picker.ARRIVE_TIME -> TimePickerPopup(f.arriveAround, { picker = null }) { v -> a.update { it.copy(arriveAround = v) } }
        Picker.BACK_TIME -> TimePickerPopup(f.backAround, { picker = null }) { v -> a.update { it.copy(backAround = v) } }
        null -> Unit
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NightsStepper(nights: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Nights before return", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = AppColors.Text.copy(alpha = alpha))
        OutlinedIconButton(onClick = { onChange((nights - 1).coerceAtLeast(0)) }, enabled = enabled && nights > 0) { Text("−", fontSize = 20.sp) }
        Text(
            "$nights",
            style = MonoStyle.copy(fontSize = 18.sp),
            color = AppColors.Text.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OutlinedIconButton(onClick = { onChange((nights + 1).coerceAtMost(30)) }, enabled = enabled && nights < 30) { Text("+", fontSize = 20.sp) }
    }
}
