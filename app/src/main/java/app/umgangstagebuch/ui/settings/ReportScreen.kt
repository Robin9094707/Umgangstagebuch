package app.umgangstagebuch.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.data.ReportOptions
import app.umgangstagebuch.ui.components.ChoiceChips
import app.umgangstagebuch.ui.components.DatePickerField
import app.umgangstagebuch.ui.components.LocalApp
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.OutlinedInfo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private enum class Period(val label: String) { M3("Letzte 3 Monate"), M6("Letzte 6 Monate"), YEAR("Dieses Jahr"), ALL("Alles"), CUSTOM("Eigener Zeitraum") }

@Composable
fun ReportScreen(nav: NavController) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsStateWithLifecycle()

    var period by remember { mutableStateOf(Period.M6) }
    var customFrom by remember { mutableStateOf(LocalDate.now().minusMonths(1)) }
    var customTo by remember { mutableStateOf(LocalDate.now()) }
    var includeVisits by remember { mutableStateOf(true) }
    var includeEntries by remember { mutableStateOf(true) }
    var includePhotos by remember { mutableStateOf(true) }
    var includeChecksums by remember { mutableStateOf(true) }
    var includeRecordings by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf(EntryCategory.entries.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Uri?>(null) }

    val earliest = (data.entries.map { it.eventDate } + data.visits.map { it.start }).minOrNull()?.let { Fmt.ld(it) } ?: LocalDate.now()
    val (from, to) = when (period) {
        Period.M3 -> LocalDate.now().minusMonths(3) to LocalDate.now()
        Period.M6 -> LocalDate.now().minusMonths(6) to LocalDate.now()
        Period.YEAR -> LocalDate.now().withDayOfYear(1) to LocalDate.now()
        Period.ALL -> earliest to LocalDate.now().plusYears(1)
        Period.CUSTOM -> customFrom to customTo
    }
    val options = ReportOptions(
        from = Fmt.millis(from), to = Fmt.millis(to, LocalTime.MAX),
        includeVisits = includeVisits, includeEntries = includeEntries, includePhotos = includePhotos,
        includeChecksums = includeChecksums, includeRecordings = includeRecordings, categories = categories,
    )
    val entryCount = data.entries.count { it.eventDate in options.from..options.to && it.category in categories }
    val visitCount = data.visits.count { it.start in options.from..options.to }

    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching { app.report.write(uri, options) }
                .onSuccess { result = uri }
                .onFailure { repo.message("PDF konnte nicht erstellt werden: ${it.message}") }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF-Bericht") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedInfo(
                "Der Bericht ist sachlich gehalten: echter Name des Vaters, Erfassungszeitpunkte, Änderungsvermerke und Prüfsummen. Ideal für Jugendamt, Verfahrensbeistand, Anwalt oder Gericht.",
                icon = Icons.Outlined.Info,
            )
            SectionCard(title = "Zeitraum") {
                ChoiceChips(Period.entries, period, { it.label }, { period = it })
                AnimatedVisibility(period == Period.CUSTOM) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DatePickerField("Von", customFrom, { customFrom = it }, Modifier.weight(1f))
                        DatePickerField("Bis", customTo, { customTo = it }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${Fmt.dateShort(from)} – ${Fmt.dateShort(to)} · $visitCount Umgänge · $entryCount Einträge", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "Inhalt") {
                Toggle("Umgangstermine & Absagen", includeVisits) { includeVisits = it }
                Toggle("Tagebucheinträge", includeEntries) { includeEntries = it }
                Toggle("Fotos einbetten", includePhotos) { includePhotos = it }
                Toggle("Tonaufnahmen auflisten", includeRecordings) { includeRecordings = it }
                Toggle("Prüfsummen anzeigen", includeChecksums) { includeChecksums = it }
            }
            if (includeEntries) {
                SectionCard(title = "Kategorien") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EntryCategory.entries.forEach { c ->
                            FilterChip(
                                selected = c in categories,
                                onClick = { categories = if (c in categories) categories - c else categories + c },
                                label = { Text(c.short) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }

            if (busy) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            } else {
                Button(
                    onClick = {
                        result = null
                        createPdf.launch("Umgangsdokumentation_${data.profile.childName.ifBlank { "Kind" }}_${Fmt.fileStamp()}.pdf")
                    },
                    enabled = from <= to,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Icon(Icons.Outlined.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text("PDF erstellen & speichern") }
            }

            result?.let { uri ->
                SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("Bericht gespeichert", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try { context.startActivity(i) } catch (_: ActivityNotFoundException) { repo.message("Keine App zum Öffnen von PDFs gefunden") }
                        }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Öffnen") }
                        FilledTonalButton(onClick = {
                            val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(i, "Bericht teilen"))
                        }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Teilen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
