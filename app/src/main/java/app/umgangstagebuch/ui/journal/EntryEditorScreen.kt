package app.umgangstagebuch.ui.journal

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.AppJson
import app.umgangstagebuch.data.Entry
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.data.Handover
import app.umgangstagebuch.data.Moods
import app.umgangstagebuch.data.Profile
import app.umgangstagebuch.data.Punctuality
import app.umgangstagebuch.data.Visit
import app.umgangstagebuch.data.VisitStatus
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.AppTextField
import app.umgangstagebuch.ui.components.ChoiceChips
import app.umgangstagebuch.ui.components.ConfirmDialog
import app.umgangstagebuch.ui.components.DateBadge
import app.umgangstagebuch.ui.components.DatePickerField
import app.umgangstagebuch.ui.components.LabeledText
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.MoodSelector
import app.umgangstagebuch.ui.components.OutlinedInfo
import app.umgangstagebuch.ui.components.PhotoEditor
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.components.TagPicker
import app.umgangstagebuch.ui.components.TimePickerField
import app.umgangstagebuch.ui.components.VisitStatusPill
import app.umgangstagebuch.ui.components.rememberPhotoActions
import app.umgangstagebuch.ui.onboarding.StepHeader
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch

private enum class WizardStep(val title: String) {
    VISIT("Umgang"), BEFORE("Vorher"), HANDOVER("Übergaben"), AFTER("Nachher"), STORY("Erzählungen"), EXTRAS("Fotos & mehr"), SUMMARY("Zusammenfassung")
}

@Composable
fun EntryEditorScreen(id: String?, visitId: String?, category: String?, wizard: Boolean, nav: NavController) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsStateWithLifecycle()
    val photoUris by repo.photos.collectAsStateWithLifecycle()
    val profile = data.profile

    val original = remember(id) { id?.let { i -> repo.data.value.entries.firstOrNull { it.id == i } } }
    val initial = remember(id) {
        original ?: run {
            val cat = category?.let { runCatching { EntryCategory.valueOf(it) }.getOrNull() } ?: EntryCategory.CHILD
            val v = visitId?.let { vid -> repo.data.value.visits.firstOrNull { it.id == vid } }
            Entry(
                category = cat,
                visitId = v?.id,
                eventDate = v?.end?.coerceAtMost(System.currentTimeMillis()) ?: System.currentTimeMillis(),
                pickup = if (cat == EntryCategory.VISIT_REPORT) Handover() else null,
                dropoff = if (cat == EntryCategory.VISIT_REPORT) Handover() else null,
            )
        }
    }
    var draft by rememberSaveable(id, stateSaver = EntrySaver) { mutableStateOf(initial) }
    var addedPhotoNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var markVisitDone by rememberSaveable { mutableStateOf(true) }
    val useWizard = wizard && original == null

    val photoActions = rememberPhotoActions { uris: List<Uri> ->
        scope.launch {
            uris.forEach { u ->
                repo.addPhoto(u, draft.id)?.let { p ->
                    addedPhotoNames = addedPhotoNames + p.fileName
                    draft = draft.copy(photos = draft.photos + p)
                }
            }
        }
    }

    fun leave() {
        scope.launch { if (addedPhotoNames.isNotEmpty()) repo.cleanupPhotos(addedPhotoNames) }
        nav.popBackStack()
    }
    val dirty = draft != initial
    BackHandler(enabled = dirty) { confirmDiscard = true }

    fun save() {
        saving = true
        scope.launch {
            val saved = repo.saveEntry(draft)
            val linked = draft.visitId?.let { vid -> repo.data.value.visits.firstOrNull { it.id == vid } }
            if (useWizard && linked != null && linked.status == VisitStatus.PLANNED && markVisitDone) {
                val missed = draft.pickup?.punctuality == Punctuality.MISSED
                repo.saveVisit(linked.copy(status = if (missed) VisitStatus.NO_SHOW else VisitStatus.DONE))
            }
            val removed = addedPhotoNames.filterNot { n -> saved.photos.any { it.fileName == n } }
            if (removed.isNotEmpty()) repo.cleanupPhotos(removed)
            repo.message(if (original == null) "Eintrag gespeichert – erfasst am ${Fmt.dateTime(saved.createdAt)} Uhr" else "Änderungen gespeichert")
            nav.popBackStack()
            if (original == null) nav.navigate(Routes.entry(saved.id))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Änderungen verwerfen?",
            text = "Was du bisher eingegeben hast, geht verloren.",
            confirm = "Verwerfen",
            destructive = true,
            onConfirm = { leave() },
            onDismiss = { confirmDiscard = false },
        )
    }

    if (useWizard) {
        Wizard(
            draft = draft, onChange = { draft = it }, profile = profile, visits = data.visits, photoUris = photoUris,
            onAddGallery = photoActions.pickFromGallery, onAddCamera = photoActions.takePhoto,
            markVisitDone = markVisitDone, onMarkVisitDone = { markVisitDone = it },
            saving = saving,
            onClose = { if (dirty) confirmDiscard = true else leave() },
            onSave = ::save,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Neuer Eintrag" else "Eintrag bearbeiten") },
                navigationIcon = { IconButton(onClick = { if (dirty) confirmDiscard = true else leave() }) { Icon(Icons.Rounded.Close, "Schließen") } },
                actions = {
                    TextButton(onClick = ::save, enabled = !saving && dirty && draft.hasContent()) { Text("Speichern") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (original != null) {
                OutlinedInfo(
                    "Erfasst am ${Fmt.stamp(original.createdAt)}. Änderungen werden mit Zeitpunkt im Verlauf gespeichert – die ursprüngliche Fassung bleibt nachvollziehbar.",
                    icon = Icons.Outlined.Verified,
                )
            }
            SectionCard(title = "Art des Eintrags") {
                ChoiceChips(EntryCategory.entries, draft.category, { it.label }, { c ->
                    draft = draft.copy(
                        category = c,
                        pickup = draft.pickup ?: if (c == EntryCategory.VISIT_REPORT || c == EntryCategory.HANDOVER) Handover() else null,
                        dropoff = draft.dropoff ?: if (c == EntryCategory.VISIT_REPORT) Handover() else null,
                    )
                })
            }
            SectionCard(title = "Wann?") {
                EventDateTime(draft) { draft = it }
                Spacer(Modifier.height(12.dp))
                VisitLink(draft, data.visits) { draft = it }
            }
            SectionCard(title = "Was ist passiert?") {
                AppTextField(draft.title, { draft = draft.copy(title = it) }, "Überschrift (optional)", singleLine = true)
                Spacer(Modifier.height(10.dp))
                AppTextField(draft.text, { draft = draft.copy(text = it) }, "Beschreibung", minLines = 5, placeholder = draft.category.hint(profile))
            }
            if (draft.category == EntryCategory.VISIT_REPORT || draft.category == EntryCategory.HANDOVER) {
                SectionCard(title = "Übergaben") { HandoverFields(draft, profile) { draft = it } }
            }
            if (draft.category == EntryCategory.VISIT_REPORT) {
                SectionCard(title = "Vorher") {
                    MoodSelector(draft.moodBefore, { draft = draft.copy(moodBefore = it) })
                    Spacer(Modifier.height(12.dp))
                    TagPicker(draft.tagsBefore, { draft = draft.copy(tagsBefore = it) })
                }
            }
            SectionCard(title = if (draft.category == EntryCategory.VISIT_REPORT) "Nachher" else "Befinden von ${profile.childDisplay}") {
                MoodSelector(draft.moodAfter, { draft = draft.copy(moodAfter = it) })
                Spacer(Modifier.height(12.dp))
                TagPicker(draft.tags, { draft = draft.copy(tags = it) })
            }
            SectionCard(title = "Äußerungen von ${profile.childDisplay}", icon = Icons.Outlined.FormatQuote) {
                QuotesField(draft, profile) { draft = it }
            }
            SectionCard(title = "Weitere Angaben") {
                AppTextField(draft.health, { draft = draft.copy(health = it) }, "Gesundheit / Auffälligkeiten", minLines = 2)
                Spacer(Modifier.height(10.dp))
                AppTextField(draft.witnesses, { draft = draft.copy(witnesses = it) }, "Zeugen / Beteiligte", placeholder = "z. B. Oma, Erzieherin Frau M.")
            }
            SectionCard(title = "Fotos & Screenshots") {
                PhotoEditor(draft.photos, photoUris, photoActions.pickFromGallery, photoActions.takePhoto, { p -> draft = draft.copy(photos = draft.photos - p) })
            }
            Button(
                onClick = ::save,
                enabled = !saving && dirty && draft.hasContent(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Speichern") }
        }
    }
}

/** Entwurf übersteht Drehen, Theme-Wechsel und Prozess-Neustart. */
private val EntrySaver = Saver<Entry, String>(
    save = { AppJson.encodeToString(Entry.serializer(), it) },
    restore = { AppJson.decodeFromString(Entry.serializer(), it) },
)

private fun Entry.hasContent() = title.isNotBlank() || text.isNotBlank() || childQuotes.isNotBlank() || moodBefore != null ||
    moodAfter != null || tags.isNotEmpty() || tagsBefore.isNotEmpty() || photos.isNotEmpty() || health.isNotBlank() ||
    pickup?.punctuality != null || dropoff?.punctuality != null || pickup?.note?.isNotBlank() == true || dropoff?.note?.isNotBlank() == true

fun EntryCategory.hint(p: Profile): String = when (this) {
    EntryCategory.VISIT_REPORT -> "Wie lief der Umgang insgesamt? Was ist dir aufgefallen?"
    EntryCategory.HANDOVER -> "Wie lief die Übergabe ab? Wer war dabei, was wurde gesagt?"
    EntryCategory.CHILD -> "Wie geht es ${p.childDisplay}? Was hast du beobachtet?"
    EntryCategory.COMMUNICATION -> "Was hat ${p.fatherDisplay} geschrieben oder gesagt? Möglichst wörtlich – Screenshots als Foto anhängen."
    EntryCategory.HEALTH -> "Symptome, Arztbesuch, Diagnose, Medikamente …"
    EntryCategory.SCHOOL -> "Rückmeldung von Erzieher*innen oder Lehrkräften …"
    EntryCategory.OFFICIAL -> "Termin, Gesprächspartner, Inhalt, Ergebnis, Aktenzeichen …"
    EntryCategory.OTHER -> "Was möchtest du festhalten?"
}

@Composable
private fun EventDateTime(draft: Entry, onChange: (Entry) -> Unit) {
    val dt = Fmt.ldt(draft.eventDate)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DatePickerField("Datum", dt.toLocalDate(), { d -> onChange(draft.copy(eventDate = Fmt.millis(d.atTime(dt.toLocalTime())))) }, Modifier.weight(1.4f))
        TimePickerField("Uhrzeit", dt.toLocalTime().withSecond(0).withNano(0), { t -> onChange(draft.copy(eventDate = Fmt.millis(dt.toLocalDate().atTime(t)))) }, Modifier.weight(1f))
    }
}

@Composable
private fun VisitLink(draft: Entry, visits: List<Visit>, onChange: (Entry) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val linked = visits.firstOrNull { it.id == draft.visitId }
    Surface(onClick = { open = !open }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (linked != null) Icons.Outlined.Link else Icons.Outlined.LinkOff, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Bezug zu einem Umgang", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(linked?.let { "${it.type.label}, ${Fmt.date(it.start)}" } ?: "Kein Bezug", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    AnimatedVisibility(open) {
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { onChange(draft.copy(visitId = null)); open = false }) { Text("Kein Bezug") }
            candidateVisits(visits).forEach { v ->
                VisitChoice(v, v.id == draft.visitId) { onChange(draft.copy(visitId = v.id)); open = false }
            }
        }
    }
}

private fun candidateVisits(visits: List<Visit>): List<Visit> {
    val now = System.currentTimeMillis()
    return visits.filter { it.start < now + 24L * 3600_000 && it.start > now - 60L * 24 * 3600_000 }.sortedByDescending { it.start }.take(12)
}

@Composable
private fun VisitChoice(v: Visit, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DateBadge(v.start)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.type.label, style = MaterialTheme.typography.titleSmall)
                Text("${Fmt.time(v.start)} – ${Fmt.time(v.end)} Uhr", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                VisitStatusPill(v)
            }
            if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HandoverFields(draft: Entry, p: Profile, onChange: (Entry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Abholung durch ${p.fatherDisplay}", style = MaterialTheme.typography.titleSmall)
        HandoverEditor(draft.pickup ?: Handover(), "Wie verhielt er sich bei der Abholung?") { onChange(draft.copy(pickup = it)) }
        if (draft.category == EntryCategory.VISIT_REPORT) {
            Spacer(Modifier.height(6.dp))
            Text("Rückgabe an dich", style = MaterialTheme.typography.titleSmall)
            HandoverEditor(draft.dropoff ?: Handover(), "Wie lief die Rückgabe? In welchem Zustand kam ${p.childDisplay} zurück?") { onChange(draft.copy(dropoff = it)) }
        }
    }
}

@Composable
private fun HandoverEditor(h: Handover, notePlaceholder: String, onChange: (Handover) -> Unit) {
    ChoiceChips(Punctuality.entries, h.punctuality, { it.label }, { onChange(h.copy(punctuality = if (h.punctuality == it) null else it)) })
    AnimatedVisibility(h.punctuality == Punctuality.LATE || h.punctuality == Punctuality.EARLY) {
        OutlinedTextField(
            value = h.delayMinutes?.toString().orEmpty(),
            onValueChange = { v -> onChange(h.copy(delayMinutes = v.filter(Char::isDigit).take(4).toIntOrNull())) },
            label = { Text("Wie viele Minuten?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    AppTextField(h.note, { onChange(h.copy(note = it)) }, "Notiz zur Übergabe", minLines = 2, placeholder = notePlaceholder)
}

@Composable
private fun QuotesField(draft: Entry, p: Profile, onChange: (Entry) -> Unit) {
    Text(
        "Schreibe Aussagen möglichst wörtlich auf – genau so, wie ${p.childDisplay} es gesagt hat, ohne nachzufragen oder zu bewerten.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    AppTextField(draft.childQuotes, { onChange(draft.copy(childQuotes = it)) }, "Wörtliche Aussagen", minLines = 3, placeholder = "„Papa hat gesagt, …“")
}

@Composable
private fun Wizard(
    draft: Entry,
    onChange: (Entry) -> Unit,
    profile: Profile,
    visits: List<Visit>,
    photoUris: Map<String, Uri>,
    onAddGallery: () -> Unit,
    onAddCamera: () -> Unit,
    markVisitDone: Boolean,
    onMarkVisitDone: (Boolean) -> Unit,
    saving: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    val steps = WizardStep.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val progress by animateFloatAsState((index + 1f) / steps.size, label = "wizard")
    val child = profile.childDisplay
    val father = profile.fatherDisplay

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Umgangsbericht") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Schließen") } },
                    actions = { Text("${index + 1} / ${steps.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp)) },
                )
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (index > 0) {
                    OutlinedButton(onClick = { index-- }, modifier = Modifier.height(56.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                }
                if (step == WizardStep.SUMMARY) {
                    Button(onClick = onSave, enabled = !saving, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(56.dp)) {
                        Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Bericht speichern")
                    }
                } else {
                    Button(onClick = { index++ }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(if (index == 0) "Los geht's" else "Weiter"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                    }
                }
            }
        },
    ) { padding ->
        BackHandler(enabled = index > 0) { index-- }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val fwd = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (fwd) it / 4 else -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { if (fwd) -it / 4 else it / 4 } + fadeOut())
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "wizardStep",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (s) {
                    WizardStep.VISIT -> {
                        StepHeader("🗓️", "Um welchen Umgang geht es?", "Wähle den Termin aus oder trage Datum und Uhrzeit selbst ein.")
                        val candidates = candidateVisits(visits)
                        candidates.forEach { v ->
                            VisitChoice(v, v.id == draft.visitId) {
                                onChange(draft.copy(visitId = v.id, eventDate = v.end.coerceAtMost(System.currentTimeMillis())))
                            }
                        }
                        Surface(
                            onClick = { onChange(draft.copy(visitId = null)) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (draft.visitId == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Ohne geplanten Termin", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                        }
                        if (draft.visitId == null) EventDateTime(draft, onChange)
                    }
                    WizardStep.BEFORE -> {
                        StepHeader("🌤️", "Wie ging es $child vorher?", "Denk an die Zeit vor der Abholung – am Morgen, beim Packen, auf dem Weg.")
                        SectionCard { MoodSelector(draft.moodBefore, { onChange(draft.copy(moodBefore = it)) }) }
                        SectionCard(title = "Was ist dir aufgefallen?") { TagPicker(draft.tagsBefore, { onChange(draft.copy(tagsBefore = it)) }) }
                    }
                    WizardStep.HANDOVER -> {
                        StepHeader("🤝", "Wie liefen die Übergaben?", "Pünktlichkeit und Verhalten von $father bei Abholung und Rückgabe.")
                        SectionCard { HandoverFields(draft, profile, onChange) }
                    }
                    WizardStep.AFTER -> {
                        StepHeader("🌙", "Wie ging es $child danach?", "Nach der Rückkehr, am Abend und in der Nacht.")
                        SectionCard { MoodSelector(draft.moodAfter, { onChange(draft.copy(moodAfter = it)) }) }
                        SectionCard(title = "Was ist dir aufgefallen?") { TagPicker(draft.tags, { onChange(draft.copy(tags = it)) }) }
                    }
                    WizardStep.STORY -> {
                        StepHeader("💬", "Was hat $child erzählt?", "Und was hast du selbst beobachtet?")
                        SectionCard(title = "Äußerungen", icon = Icons.Outlined.FormatQuote) { QuotesField(draft, profile, onChange) }
                        SectionCard(title = "Deine Beobachtungen") {
                            AppTextField(draft.text, { onChange(draft.copy(text = it)) }, "Was ist passiert?", minLines = 5, placeholder = EntryCategory.VISIT_REPORT.hint(profile))
                        }
                    }
                    WizardStep.EXTRAS -> {
                        StepHeader("📸", "Noch etwas?", "Fotos (z. B. von Verletzungen, Kleidung, Nachrichten), Gesundheit und Zeugen.")
                        SectionCard(title = "Fotos & Screenshots") {
                            PhotoEditor(draft.photos, photoUris, onAddGallery, onAddCamera, { p -> onChange(draft.copy(photos = draft.photos - p)) })
                        }
                        SectionCard {
                            AppTextField(draft.health, { onChange(draft.copy(health = it)) }, "Gesundheit / Auffälligkeiten", minLines = 2)
                            Spacer(Modifier.height(10.dp))
                            AppTextField(draft.witnesses, { onChange(draft.copy(witnesses = it)) }, "Zeugen / Beteiligte")
                        }
                    }
                    WizardStep.SUMMARY -> Summary(draft, profile, visits, markVisitDone, onMarkVisitDone)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Summary(draft: Entry, p: Profile, visits: List<Visit>, markVisitDone: Boolean, onMarkVisitDone: (Boolean) -> Unit) {
    StepHeader("✅", "Alles richtig so?", "Beim Speichern werden Datum und Uhrzeit automatisch festgehalten.")
    val visit = visits.firstOrNull { it.id == draft.visitId }
    SectionCard {
        LabeledText("Umgang", visit?.let { Fmt.range(it.start, it.end) } ?: Fmt.dateTime(draft.eventDate) + " Uhr")
        LabeledText("Vorher", listOfNotNull(draft.moodBefore?.let { "${Moods.emojiOf(it)} ${Moods.labelOf(it)}" }, draft.tagsBefore.joinToString(", ").ifBlank { null }).joinToString(" · "))
        draft.pickup?.let { h -> LabeledText("Abholung", listOfNotNull(h.punctuality?.label, h.delayMinutes?.let { "$it Min." }, h.note.ifBlank { null }).joinToString(" · ")) }
        draft.dropoff?.let { h -> LabeledText("Rückgabe", listOfNotNull(h.punctuality?.label, h.delayMinutes?.let { "$it Min." }, h.note.ifBlank { null }).joinToString(" · ")) }
        LabeledText("Nachher", listOfNotNull(draft.moodAfter?.let { "${Moods.emojiOf(it)} ${Moods.labelOf(it)}" }, draft.tags.joinToString(", ").ifBlank { null }).joinToString(" · "))
        LabeledText("Äußerungen von ${p.childDisplay}", draft.childQuotes)
        LabeledText("Beobachtungen", draft.text)
        LabeledText("Gesundheit", draft.health)
        LabeledText("Zeugen", draft.witnesses)
        if (draft.photos.isNotEmpty()) LabeledText("Fotos", "${draft.photos.size} angehängt")
    }
    if (visit != null && visit.status == VisitStatus.PLANNED) {
        Surface(onClick = { onMarkVisitDone(!markVisitDone) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(markVisitDone, onMarkVisitDone)
                Text(
                    if (draft.pickup?.punctuality == Punctuality.MISSED) "Umgang als „Nicht erschienen“ markieren" else "Umgang als „Stattgefunden“ markieren",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
    OutlinedInfo("Du kannst den Bericht später ergänzen. Jede Änderung wird mit Zeitstempel protokolliert.", icon = Icons.Outlined.Info)
    Spacer(Modifier.size(8.dp))
}
