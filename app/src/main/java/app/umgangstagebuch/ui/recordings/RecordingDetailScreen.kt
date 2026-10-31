package app.umgangstagebuch.ui.recordings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.GeoPoint
import app.umgangstagebuch.data.Recording
import app.umgangstagebuch.ui.components.ConfirmDialog
import app.umgangstagebuch.ui.components.LabeledText
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.theme.statusColors
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun RecordingDetailScreen(id: String, nav: NavController) {
    val repo = LocalRepo.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsStateWithLifecycle()
    val files by repo.recordingFiles.collectAsStateWithLifecycle()
    val rec = data.recordings.firstOrNull { it.id == id }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (rec == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val uri = files[rec.fileName]
    val player = rememberAudioPlayer(uri, rec.durationMs)
    val sc = statusColors

    Scaffold(rec, nav, onRename = { showRename = true }, onDelete = { showDelete = true }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Player
            SectionCard(container = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(rec.displayTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(Fmt.dateLong(rec.startedAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                WaveformBars(
                    rec.waveform,
                    player.progress,
                    Modifier.fillMaxWidth().height(80.dp),
                    playedColor = MaterialTheme.colorScheme.primary,
                    remainingColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onSeek = { player.seekTo(it) },
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { player.toggle() }, enabled = uri != null) {
                            Icon(
                                if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (player.playing) "Pause" else "Abspielen",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "${formatClock(player.positionMs)} / ${formatClock(if (player.durationMs > 0) player.durationMs.toLong() else rec.durationMs)}",
                        style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                    )
                }
                if (uri == null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Audiodatei im Ordner nicht gefunden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Ort
            rec.location?.let { loc -> LocationCard(loc, context) }

            // Feste Daten (Beweis)
            SectionCard(title = "Aufnahmedaten", icon = Icons.Outlined.Schedule) {
                LabeledText("Beginn", "${Fmt.dateLong(rec.startedAt)}, ${Fmt.time(rec.startedAt)} Uhr")
                LabeledText("Dauer", formatClock(rec.durationMs))
                LabeledText("Gespeichert am", Fmt.stamp(rec.createdAt))
                LabeledText("Datei", rec.fileName)
                Text("Diese Angaben sind fest und können nicht geändert werden – nur der Titel lässt sich anpassen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }

            // Nachweis
            SectionCard(title = "Nachweis", icon = Icons.Outlined.Verified, container = sc.goodContainer.copy(alpha = 0.45f)) {
                Text("Prüfsumme (SHA-256)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(rec.sha256.ifBlank { "–" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                Text("Die Prüfsumme belegt, dass die Audiodatei seit der Aufnahme nicht verändert wurde.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (rec.renames.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Umbenennungen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    rec.renames.forEach { rn ->
                        Text("${Fmt.dateTime(rn.at)}: „${rn.from.ifBlank { "Aufnahme" }}“ → „${rn.to}“", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showRename) {
        RenameDialog(rec.displayTitle(), onDismiss = { showRename = false }) { newName ->
            scope.launch { repo.renameRecording(rec.id, newName) }
            showRename = false
        }
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Aufnahme löschen?",
            text = "Die Audiodatei „${rec.displayTitle()}" + "“ wird endgültig gelöscht. Das lässt sich nicht rückgängig machen.",
            confirm = "Löschen",
            destructive = true,
            icon = Icons.Outlined.Delete,
            onConfirm = { nav.popBackStack(); repo.launch { deleteRecording(rec.id) } },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun Scaffold(
    rec: Recording,
    nav: NavController,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(rec.displayTitle(), maxLines = 1) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = onRename) { Icon(Icons.Outlined.DriveFileRenameOutline, "Umbenennen") }
                    IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Löschen") }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun LocationCard(loc: GeoPoint, context: android.content.Context) {
    SectionCard(title = "Ort der Aufnahme", icon = Icons.Outlined.LocationOn) {
        // Dezente „Karten"-Fläche mit Markierung; die echte Karte öffnet die Karten-App.
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(10.dp))
        LabeledText("Koordinaten", loc.pretty())
        loc.accuracy?.let { LabeledText("Genauigkeit", "±${it.toInt()} m") }
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(onClick = {
            val uri = Uri.parse("geo:${loc.lat},${loc.lng}?q=${loc.lat},${loc.lng}(Aufnahme)")
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                .onFailure {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${loc.lat}&mlon=${loc.lng}#map=17/${loc.lat}/${loc.lng}"))) }
                }
        }) { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Auf der Karte anzeigen") }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
        title = { Text("Aufnahme umbenennen") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Fingerprint, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Datum, Uhrzeit, Ort und Prüfsumme bleiben unverändert.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
