package app.umgangstagebuch.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.data.Handover
import app.umgangstagebuch.data.Moods
import app.umgangstagebuch.data.Punctuality
import app.umgangstagebuch.data.Tags
import app.umgangstagebuch.data.entryFromJson
import app.umgangstagebuch.data.isIntact
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.ConfirmDialog
import app.umgangstagebuch.ui.components.IconBadge
import app.umgangstagebuch.ui.components.LabeledText
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.Pill
import app.umgangstagebuch.ui.components.PhotoGrid
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.theme.statusColors
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun EntryDetailScreen(id: String, nav: NavController) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsStateWithLifecycle()
    val photos by repo.photos.collectAsStateWithLifecycle()
    val entry = data.entries.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    if (entry == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val p = data.profile
    val sc = statusColors
    val visit = data.visits.firstOrNull { it.id == entry.visitId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry.category.label) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.entryEdit(id = entry.id)) }) { Icon(Icons.Outlined.Edit, "Bearbeiten") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Löschen") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (bg, fg) = entry.category.colors()
                IconBadge(entry.category.icon, color = bg, contentColor = fg, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(entry.displayTitle(p.childDisplay), style = MaterialTheme.typography.headlineSmall)
                    Text("${Fmt.dateLong(entry.eventDate)}, ${Fmt.time(entry.eventDate)} Uhr", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            visit?.let { v ->
                SectionCard(onClick = { nav.navigate(Routes.visit(v.id)) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    Text("Zum Umgang", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${v.type.label} · ${Fmt.range(v.start, v.end)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (entry.moodBefore != null || entry.moodAfter != null || entry.tags.isNotEmpty() || entry.tagsBefore.isNotEmpty()) {
                SectionCard(title = "Befinden von ${p.childDisplay}") {
                    if (entry.category == EntryCategory.VISIT_REPORT) {
                        MoodRow("Vorher", entry.moodBefore, entry.tagsBefore)
                        Spacer(Modifier.height(10.dp))
                        MoodRow("Nachher", entry.moodAfter, entry.tags)
                    } else {
                        MoodRow("Stimmung", entry.moodAfter ?: entry.moodBefore, entry.tags + entry.tagsBefore)
                    }
                }
            }
            if (entry.pickup.hasData() || entry.dropoff.hasData()) {
                SectionCard(title = "Übergaben") {
                    HandoverText("Abholung", entry.pickup)
                    HandoverText("Rückgabe", entry.dropoff)
                }
            }
            if (entry.childQuotes.isNotBlank()) {
                SectionCard(title = "Äußerungen von ${p.childDisplay}", icon = Icons.Outlined.FormatQuote, container = MaterialTheme.colorScheme.tertiaryContainer) {
                    SelectionContainer {
                        Text(entry.childQuotes, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            if (entry.text.isNotBlank() || entry.health.isNotBlank() || entry.witnesses.isNotBlank()) {
                SectionCard(title = "Notizen") {
                    SelectionContainer {
                        Column {
                            LabeledText("Beschreibung", entry.text)
                            LabeledText("Gesundheit / Auffälligkeiten", entry.health)
                            LabeledText("Zeugen / Beteiligte", entry.witnesses)
                        }
                    }
                }
            }
            if (entry.photos.isNotEmpty()) {
                SectionCard(title = "Fotos (${entry.photos.size})") { PhotoGrid(entry.photos, photos) }
            }

            // Nachweis
            val intact = entry.isIntact()
            SectionCard(
                title = "Nachweis",
                icon = if (intact) Icons.Outlined.Verified else Icons.Outlined.GppBad,
                container = if (intact) sc.goodContainer.copy(alpha = 0.45f) else sc.badContainer,
            ) {
                LabeledText("Erfasst am (automatisch)", Fmt.stamp(entry.createdAt))
                if (entry.isEdited) LabeledText("Zuletzt geändert", "${Fmt.stamp(entry.updatedAt)} · ${entry.revisions.size}× bearbeitet")
                Text("Prüfsumme (SHA-256)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(entry.hash, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (intact) "Der Inhalt stimmt mit der Prüfsumme überein." else "Achtung: Der Inhalt passt nicht zur gespeicherten Prüfsumme (Datei wurde außerhalb der App verändert).",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (entry.isEdited) {
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Outlined.History, null); Spacer(Modifier.width(6.dp)); Text(if (showHistory) "Verlauf ausblenden" else "Frühere Fassungen anzeigen")
                    }
                    if (showHistory) {
                        entry.revisions.reversed().forEach { r ->
                            val old = entryFromJson(r.previousJson)
                            SectionCard(container = MaterialTheme.colorScheme.surface, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Text("Fassung vor der Änderung am ${Fmt.dateTime(r.editedAt)} Uhr", style = MaterialTheme.typography.labelLarge)
                                old?.let { o ->
                                    LabeledText("Überschrift", o.title)
                                    LabeledText("Beschreibung", o.text)
                                    LabeledText("Äußerungen", o.childQuotes)
                                    LabeledText("Gesundheit", o.health)
                                    if (o.tags.isNotEmpty()) LabeledText("Beobachtungen", o.tags.joinToString(", "))
                                }
                                Text("Prüfsumme: ${r.previousHash.take(16)}…", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Eintrag löschen?",
            text = "Der Eintrag und seine Fotos werden endgültig entfernt. Als Nachweis ist es meist besser, Einträge zu ergänzen statt zu löschen.",
            confirm = "Löschen",
            destructive = true,
            icon = Icons.Outlined.Delete,
            onConfirm = { scope.launch { repo.deleteEntry(entry.id); nav.popBackStack() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun Handover?.hasData() = this != null && (punctuality != null || note.isNotBlank())

@Composable
private fun HandoverText(label: String, h: Handover?) {
    if (!h.hasData()) return
    h!!
    val sc = statusColors
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            h.punctuality?.let { pu ->
                val (bg, fg) = when (pu) {
                    Punctuality.ON_TIME -> sc.goodContainer to sc.onGoodContainer
                    Punctuality.MISSED -> sc.badContainer to sc.onBadContainer
                    else -> sc.warnContainer to sc.onWarnContainer
                }
                Pill(pu.label + (h.delayMinutes?.takeIf { pu == Punctuality.LATE || pu == Punctuality.EARLY }?.let { " · $it Min." } ?: ""), bg, fg)
            }
        }
        if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MoodRow(label: String, mood: Int?, tags: List<String>) {
    val sc = statusColors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("${Moods.emojiOf(mood)}  ${Moods.labelOf(mood)}", style = MaterialTheme.typography.titleMedium)
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.distinct().forEach { t ->
                    if (t in Tags.concerning) Pill(t, sc.warnContainer, sc.onWarnContainer)
                    else Pill(t, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
