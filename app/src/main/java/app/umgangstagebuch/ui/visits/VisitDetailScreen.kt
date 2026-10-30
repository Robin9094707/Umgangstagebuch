package app.umgangstagebuch.ui.visits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.Cancellation
import app.umgangstagebuch.data.CancelledBy
import app.umgangstagebuch.data.Channel
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.data.Visit
import app.umgangstagebuch.data.VisitStatus
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.AppTextField
import app.umgangstagebuch.ui.components.ChoiceChips
import app.umgangstagebuch.ui.components.DatePickerField
import app.umgangstagebuch.ui.components.LabeledText
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.components.TimePickerField
import app.umgangstagebuch.ui.components.VisitStatusPill
import app.umgangstagebuch.ui.journal.EntryCard
import app.umgangstagebuch.ui.theme.statusColors
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun VisitDetailScreen(id: String, nav: NavController) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsStateWithLifecycle()
    val photos by repo.photos.collectAsStateWithLifecycle()
    val visit = data.visits.firstOrNull { it.id == id }
    var showCancel by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (visit == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val p = data.profile
    val entries = data.entries.filter { it.visitId == id }
    val sc = statusColors
    fun setStatus(s: VisitStatus) = scope.launch { repo.saveVisit(visit.copy(status = s, cancellation = if (s == VisitStatus.CANCELLED) visit.cancellation else null)) }
    val isPast = visit.start < System.currentTimeMillis()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(visit.type.label) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.visitEdit(visit.id)) }) { Icon(Icons.Outlined.Edit, "Bearbeiten") }
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Outlined.Delete, "Löschen") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                val fg = MaterialTheme.colorScheme.onSecondaryContainer
                Text(Fmt.dateLong(visit.start), style = MaterialTheme.typography.headlineSmall, color = fg)
                Text(Fmt.relativeDay(visit.start).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = fg.copy(alpha = 0.8f))
                Spacer(Modifier.height(12.dp))
                Line(Icons.Outlined.Schedule, Fmt.range(visit.start, visit.end), fg)
                Line(Icons.Outlined.Timer, "Dauer: ${Fmt.duration(visit.start, visit.end)}", fg)
                if (visit.location.isNotBlank()) Line(Icons.Outlined.Place, visit.location, fg)
                Spacer(Modifier.height(10.dp))
                VisitStatusPill(visit)
                if (visit.note.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(visit.note, color = fg) }
            }

            // Absage-Details
            visit.cancellation?.takeIf { visit.status == VisitStatus.CANCELLED }?.let { c ->
                SectionCard(title = "Absage", icon = Icons.Outlined.EventBusy, container = sc.badContainer.copy(alpha = 0.6f)) {
                    LabeledText("Abgesagt von", if (c.by == CancelledBy.FATHER) p.fatherDisplay else c.by.label)
                    c.notifiedAt?.let { n ->
                        val h = c.noticeHours(visit.start) ?: 0
                        LabeledText("Mitgeteilt", "${Fmt.dateTime(n)} Uhr über ${c.channel.label}")
                        LabeledText("Vorlaufzeit", if (h >= 0) "$h Stunden vor Beginn" + (if (h < 24) " – kurzfristig" else "") else "erst nach dem geplanten Beginn")
                    }
                    LabeledText("Begründung", c.reason)
                    if (c.replacementOffered) LabeledText("Ersatztermin", "wurde angeboten")
                    TextButton(onClick = { showCancel = true }) { Text("Absage bearbeiten") }
                }
            }

            // Status
            SectionCard(title = "Wie lief es?") {
                if (!isPast && visit.status == VisitStatus.PLANNED) {
                    Text("Der Umgang liegt in der Zukunft. Du kannst eine Absage bereits jetzt erfassen.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusButton("Stattgefunden", Icons.Outlined.TaskAlt, visit.status == VisitStatus.DONE, sc.goodContainer, sc.onGoodContainer) { setStatus(VisitStatus.DONE) }
                    StatusButton("Verkürzt", Icons.Outlined.ContentCut, visit.status == VisitStatus.SHORTENED, sc.warnContainer, sc.onWarnContainer) { setStatus(VisitStatus.SHORTENED) }
                    StatusButton("Abgesagt", Icons.Outlined.Cancel, visit.status == VisitStatus.CANCELLED, sc.badContainer, sc.onBadContainer) { showCancel = true }
                    run {
                        StatusButton("Nicht erschienen", Icons.Outlined.NoAccounts, visit.status == VisitStatus.NO_SHOW, sc.badContainer, sc.onBadContainer) { setStatus(VisitStatus.NO_SHOW) }
                    }
                    if (visit.status != VisitStatus.PLANNED) {
                        StatusButton("Zurücksetzen", Icons.Outlined.Replay, false, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant) { setStatus(VisitStatus.PLANNED) }
                    }
                }
            }

            // Berichte
            SectionCard(title = "Berichte & Notizen") {
                if (entries.isEmpty()) {
                    Text("Noch kein Bericht zu diesem Umgang.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    entries.forEach { e ->
                        EntryCard(e, p.childDisplay, photos, onClick = { nav.navigate(Routes.entry(e.id)) }, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { nav.navigate(Routes.entryEdit(visitId = visit.id, category = EntryCategory.VISIT_REPORT.name, wizard = true)) },
                        shapes = ButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Bericht") }
                    FilledTonalButton(onClick = { nav.navigate(Routes.entryEdit(visitId = visit.id)) }) { Text("Notiz") }
                }
            }
        }
    }

    if (showCancel) CancelSheet(visit, p.fatherDisplay, onDismiss = { showCancel = false }) { c ->
        scope.launch { repo.saveVisit(visit.copy(status = VisitStatus.CANCELLED, cancellation = c)) }
        showCancel = false
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text("Umgang löschen?") },
            text = {
                Text(
                    "Gelöscht werden sollte nur ein versehentlich angelegter Termin. Abgesagte Termine besser als „Abgesagt“ markieren – " +
                        "so bleiben sie als Nachweis erhalten. Berichte bleiben erhalten.",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { scope.launch { repo.deleteVisit(visit.id); nav.popBackStack() }; showDelete = false }) {
                        Text("Nur diesen Termin", color = MaterialTheme.colorScheme.error)
                    }
                    if (visit.seriesId != null) {
                        TextButton(onClick = { scope.launch { repo.deleteVisit(visit.id, deleteSeries = true); nav.popBackStack() }; showDelete = false }) {
                            Text("Alle künftigen dieser Serie", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Line(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun StatusButton(text: String, icon: ImageVector, selected: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.filledTonalButtonColors(containerColor = bg, contentColor = fg) else ButtonDefaults.filledTonalButtonColors(),
        shapes = ButtonDefaults.shapes(),
    ) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text)
    }
}

@Composable
private fun CancelSheet(visit: Visit, fatherName: String, onDismiss: () -> Unit, onSave: (Cancellation) -> Unit) {
    val existing = visit.cancellation
    var by by remember { mutableStateOf(existing?.by ?: CancelledBy.FATHER) }
    var channel by remember { mutableStateOf(existing?.channel ?: Channel.WHATSAPP) }
    var reason by remember { mutableStateOf(existing?.reason.orEmpty()) }
    var replacement by remember { mutableStateOf(existing?.replacementOffered ?: false) }
    val notified = existing?.notifiedAt ?: System.currentTimeMillis()
    var date by remember { mutableStateOf(Fmt.ld(notified)) }
    var time by remember { mutableStateOf(Fmt.ldt(notified).toLocalTime().withSecond(0).withNano(0)) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Absage erfassen", style = MaterialTheme.typography.headlineSmall)
            Text("Wer hat abgesagt?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(CancelledBy.entries, by, { if (it == CancelledBy.FATHER) fatherName else it.label }, { by = it })
            Text("Wann wurde abgesagt?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DatePickerField("Datum", date, { d: LocalDate -> date = d }, Modifier.weight(1.4f))
                TimePickerField("Uhrzeit", time, { t: LocalTime -> time = t }, Modifier.weight(1f))
            }
            val hours = (visit.start - Fmt.millis(date, time)) / 3_600_000L
            Text(
                if (hours >= 0) "Das war $hours Stunden vor Beginn${if (hours < 24) " – kurzfristig." else "."}" else "Die Absage kam erst nach dem geplanten Beginn.",
                style = MaterialTheme.typography.bodySmall,
                color = if (hours < 24) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Wie wurde abgesagt?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceChips(Channel.entries, channel, { it.label }, { channel = it })
            AppTextField(reason, { reason = it }, "Begründung (möglichst wörtlich)", minLines = 3, placeholder = "z. B. „Ich schaffe es heute nicht, Arbeit.“")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ersatztermin angeboten", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(replacement, { replacement = it })
            }
            Button(
                onClick = {
                    onSave(Cancellation(by, reason.trim(), Fmt.millis(date, time), channel, replacement, existing?.recordedAt ?: System.currentTimeMillis()))
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Absage speichern") }
        }
    }
}
