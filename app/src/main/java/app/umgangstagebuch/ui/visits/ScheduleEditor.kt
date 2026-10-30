package app.umgangstagebuch.ui.visits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.umgangstagebuch.data.Repeat
import app.umgangstagebuch.data.Visit
import app.umgangstagebuch.data.VisitType
import app.umgangstagebuch.data.generateSeries
import app.umgangstagebuch.ui.components.AppTextField
import app.umgangstagebuch.ui.components.ChoiceChips
import app.umgangstagebuch.ui.components.DatePickerField
import app.umgangstagebuch.ui.components.TimePickerField
import app.umgangstagebuch.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@Stable
class ScheduleState(
    startDate: LocalDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)),
    startTime: LocalTime = LocalTime.of(15, 0),
    endDate: LocalDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)).plusDays(2),
    endTime: LocalTime = LocalTime.of(18, 0),
    type: VisitType = VisitType.WEEKEND,
    location: String = "",
    note: String = "",
    repeat: Repeat = Repeat.NONE,
    until: LocalDate = LocalDate.now().plusMonths(6),
) {
    var startDate by mutableStateOf(startDate)
    var startTime by mutableStateOf(startTime)
    var endDate by mutableStateOf(endDate)
    var endTime by mutableStateOf(endTime)
    var type by mutableStateOf(type)
    var location by mutableStateOf(location)
    var note by mutableStateOf(note)
    var repeat by mutableStateOf(repeat)
    var until by mutableStateOf(until)

    val start get() = startDate.atTime(startTime)
    val end get() = endDate.atTime(endTime)
    val isValid get() = end.isAfter(start)

    fun moveStartDate(d: LocalDate) {
        val shift = java.time.temporal.ChronoUnit.DAYS.between(startDate, d)
        startDate = d
        endDate = endDate.plusDays(shift)
    }

    fun applyType(t: VisitType) {
        type = t
        when (t) {
            VisitType.WEEKEND -> if (endDate == startDate) endDate = startDate.plusDays(2)
            VisitType.OVERNIGHT -> if (endDate == startDate) endDate = startDate.plusDays(1)
            VisitType.DAY, VisitType.ACCOMPANIED, VisitType.PHONE, VisitType.VIDEO -> endDate = startDate
            else -> Unit
        }
        if (t == VisitType.PHONE || t == VisitType.VIDEO) {
            endTime = startTime.plusMinutes(30)
        }
    }

    fun build(): List<Visit> = generateSeries(start, end, repeat, until, type, location.trim(), note.trim())

    companion object {
        fun from(v: Visit) = ScheduleState(
            startDate = Fmt.ld(v.start), startTime = Fmt.ldt(v.start).toLocalTime(),
            endDate = Fmt.ld(v.end), endTime = Fmt.ldt(v.end).toLocalTime(),
            type = v.type, location = v.location, note = v.note,
        )
    }
}

@Composable
fun ScheduleEditor(state: ScheduleState, allowRepeat: Boolean, modifier: Modifier = Modifier, showNote: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Art des Umgangs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceChips(VisitType.entries, state.type, { it.label }, { state.applyType(it) })

        Text("Beginn", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DatePickerField("Datum", state.startDate, { state.moveStartDate(it) }, Modifier.weight(1.4f))
            TimePickerField("Uhrzeit", state.startTime, { state.startTime = it }, Modifier.weight(1f))
        }
        Text("Ende", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DatePickerField("Datum", state.endDate, { state.endDate = it }, Modifier.weight(1.4f))
            TimePickerField("Uhrzeit", state.endTime, { state.endTime = it }, Modifier.weight(1f))
        }
        if (!state.isValid) {
            Text("Das Ende muss nach dem Beginn liegen.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        AppTextField(state.location, { state.location = it }, "Ort / Übergabeort", leading = Icons.Outlined.Place, singleLine = true, placeholder = "z. B. Kita, Bahnhof, bei uns zu Hause")

        if (allowRepeat) {
            Text("Wiederholen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(Repeat.entries, state.repeat, { it.label }, { state.repeat = it })
            if (state.repeat != Repeat.NONE) {
                DatePickerField("Wiederholen bis", state.until, { state.until = it })
                val count = state.build().size
                Text(
                    "Es werden $count Termine angelegt. Einzelne Termine kannst du später absagen oder ändern.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showNote) {
            AppTextField(state.note, { state.note = it }, "Notiz (optional)", minLines = 2)
        }
        Spacer(Modifier.height(4.dp))
        Spacer(Modifier.width(0.dp))
    }
}
