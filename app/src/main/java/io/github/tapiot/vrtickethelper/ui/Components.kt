package io.github.tapiot.vrtickethelper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import io.github.tapiot.vrtickethelper.logic.TimeMath
import io.github.tapiot.vrtickethelper.model.Candidate
import io.github.tapiot.vrtickethelper.model.Station
import io.github.tapiot.vrtickethelper.ui.theme.AppColors
import io.github.tapiot.vrtickethelper.ui.theme.MonoStyle
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d.M.", Locale.ENGLISH)
val LongDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d.M.yyyy", Locale.ENGLISH)

@Composable
fun Heading(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onBackground) {
    Text(text.uppercase(Locale.ENGLISH), modifier = modifier, style = MaterialTheme.typography.headlineSmall, color = color)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.ENGLISH),
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = AppColors.Muted,
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, border: Color = AppColors.Outline, background: Color = AppColors.Surface, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        content = content,
    )
}

/** A tappable field that looks like a text field and opens a picker. */
@Composable
fun PickerField(label: String, value: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier
            .border(1.dp, AppColors.Outline.copy(alpha = alpha), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.Muted.copy(alpha = alpha))
        Text(value, style = MonoStyle, color = AppColors.Text.copy(alpha = alpha), maxLines = 1)
    }
}

@Composable
fun StationField(
    label: String,
    station: Station?,
    stations: List<Station>,
    modifier: Modifier = Modifier,
    onPick: (Station?) -> Unit,
) {
    var text by remember(station) { mutableStateOf(station?.name.orEmpty()) }
    var focused by remember { mutableStateOf(false) }
    val suggestions = remember(text, stations, station) {
        if (text.isBlank() || station?.name == text) emptyList() else matchStations(text, stations)
    }
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                if (station != null && it != station.name) onPick(null)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        if (focused && suggestions.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth().padding(top = 4.dp), background = AppColors.SurfaceHigh) {
                Column {
                    suggestions.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = AppColors.Outline)
                        Row(
                            Modifier.fillMaxWidth().clickable { text = s.name; onPick(s) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(s.code, style = MonoStyle.copy(fontSize = 13.sp), color = AppColors.Muted)
                        }
                    }
                }
            }
        }
    }
}

fun matchStations(query: String, stations: List<Station>): List<Station> {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return emptyList()
    val names = { s: Station -> (listOf(s.name) + s.altNames).map { it.lowercase(Locale.ROOT) } }
    val exactCode = stations.filter { it.code.lowercase(Locale.ROOT) == q }
    val prefix = stations.filter { s -> names(s).any { it.startsWith(q) } }
    val contains = stations.filter { s -> names(s).any { it.contains(q) } }
    return (exactCode + prefix + contains).distinct().take(6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerPopup(initial: LocalDate, minDate: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val minMillis = minDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minMillis
            override fun isSelectableYear(year: Int) = year >= minDate.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerPopup(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun DayMarker(offset: Int) {
    if (offset == 0) return
    Text(
        TimeMath.dayMarker(offset),
        style = MonoStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
        color = AppColors.AmberText,
        modifier = Modifier.padding(start = 2.dp),
    )
}

/** One train: departure → arrival with day markers, journey time, train names and price. */
@Composable
fun TrainRow(c: Candidate, selected: Boolean, cheapest: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val border = when {
        selected -> AppColors.BlueText
        else -> AppColors.Outline
    }
    val bg = when {
        selected -> AppColors.BlueContainer
        else -> AppColors.Surface
    }
    val alpha = if (enabled) 1f else 0.4f
    Panel(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
        border = border,
        background = bg,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(TimeMath.clock(c.journey.departure), style = MonoStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium), color = AppColors.Text.copy(alpha = alpha))
                    DayMarker(c.departureDayOffset)
                    Text("  →  ", style = MonoStyle.copy(fontSize = 18.sp), color = AppColors.Muted.copy(alpha = alpha))
                    Text(TimeMath.clock(c.journey.arrival), style = MonoStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium), color = AppColors.Text.copy(alpha = alpha))
                    DayMarker(c.arrivalDayOffset)
                }
                Spacer(Modifier.height(2.dp))
                val changes = when (c.journey.changes) { 0 -> "direct"; 1 -> "1 change"; else -> "${c.journey.changes} changes" }
                Text(
                    "${TimeMath.duration(c.journey.duration)} · $changes · ${c.journey.trainNames}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Muted.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PriceTag(c.priceCents, cheapest, alpha)
        }
    }
}

@Composable
fun PriceTag(cents: Int, cheapest: Boolean, alpha: Float = 1f) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .background(if (cheapest) AppColors.AmberBg else Color.Transparent, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cheapest) Text("★ ", color = AppColors.AmberText, fontSize = 14.sp)
        Text(
            TimeMath.price(cents),
            style = MonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
            color = (if (cheapest) AppColors.AmberText else AppColors.Text).copy(alpha = alpha),
        )
    }
}

val LockIcon: ImageVector by lazy {
    ImageVector.Builder("lock", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 10f); lineTo(6f, 8f)
            curveTo(6f, 4.7f, 8.7f, 2f, 12f, 2f)
            curveTo(15.3f, 2f, 18f, 4.7f, 18f, 8f)
            lineTo(18f, 10f); lineTo(19f, 10f)
            curveTo(19.6f, 10f, 20f, 10.4f, 20f, 11f)
            lineTo(20f, 21f)
            curveTo(20f, 21.6f, 19.6f, 22f, 19f, 22f)
            lineTo(5f, 22f)
            curveTo(4.4f, 22f, 4f, 21.6f, 4f, 21f)
            lineTo(4f, 11f)
            curveTo(4f, 10.4f, 4.4f, 10f, 5f, 10f)
            close()
            moveTo(8f, 10f); lineTo(16f, 10f); lineTo(16f, 8f)
            curveTo(16f, 5.8f, 14.2f, 4f, 12f, 4f)
            curveTo(9.8f, 4f, 8f, 5.8f, 8f, 8f)
            close()
        }
    }.build()
}

/** Shows the current site. */
@Composable
fun SiteBar(host: String?, trusted: Boolean, onClose: () -> Unit) {
    val color = if (trusted) AppColors.Text else AppColors.Warning
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (trusted) AppColors.SurfaceHigh else AppColors.WarningBg)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClose) { Text("✕", fontSize = 18.sp, color = AppColors.Text) }
        Icon(LockIcon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            host ?: "…",
            style = MonoStyle.copy(fontSize = 14.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!trusted) Text("Not vr.fi", style = MaterialTheme.typography.labelMedium, color = AppColors.Warning)
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Bullet(text: String, color: Color = AppColors.Text) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = color)
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}
