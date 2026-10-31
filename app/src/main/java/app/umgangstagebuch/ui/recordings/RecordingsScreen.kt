package app.umgangstagebuch.ui.recordings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.Recording
import app.umgangstagebuch.recording.LocationHelper
import app.umgangstagebuch.recording.Recorder
import app.umgangstagebuch.recording.RecordingService
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.EmptyState
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.OutlinedInfo
import app.umgangstagebuch.ui.components.Pill
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf

@Composable
fun RecordingsScreen(nav: NavController) {
    val repo = LocalRepo.current
    val context = LocalContext.current
    val data by repo.data.collectAsStateWithLifecycle()
    val recording by Recorder.active.collectAsStateWithLifecycle()
    val finishing by Recorder.finishing.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun launch() {
        RecordingService.start(context)
        LocationHelper.request(context) { geo -> if (geo != null && Recorder.active.value) Recorder.pendingLocation = geo }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) { permissionDenied = false; launch() } else permissionDenied = true
    }

    fun onRecordClick() {
        if (recording) {
            RecordingService.stop(context)
        } else {
            val needed = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }.toTypedArray()
            permissions.launch(needed)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Aufnahmen") },
                subtitle = { Text("${data.recordings.size} gespeichert") },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RecordPanel(recording, finishing, onClick = { onRecordClick() }) }

            if (permissionDenied) {
                item {
                    OutlinedInfo(
                        "Zum Aufnehmen braucht die App Zugriff auf das Mikrofon. Du kannst das in den Android-Einstellungen unter „Berechtigungen“ erlauben.",
                        icon = Icons.Outlined.MicOff,
                    )
                }
            }
            item {
                OutlinedInfo(
                    "Die Aufnahme läuft im Hintergrund weiter, auch wenn der Bildschirm aus ist. Android zeigt dabei eine kleine Benachrichtigung – das ist Vorschrift. " +
                        "Hinweis: Heimliche Tonaufnahmen sind vor Gericht oft nicht verwertbar und können rechtlich heikel sein (§ 201 StGB). Für dich als persönliche Dokumentation sind sie dennoch hilfreich.",
                    icon = Icons.Outlined.Info,
                )
            }

            if (data.recordings.isEmpty() && !recording) {
                item {
                    EmptyState("🎙️", "Noch keine Aufnahmen", "Tippe oben auf den Knopf, um eine Aufnahme zu starten. Datum, Uhrzeit und Ort werden automatisch festgehalten.")
                }
            } else {
                item {
                    Text("Gespeicherte Aufnahmen", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }
                items(data.recordings, key = { it.id }) { r ->
                    RecordingRow(r, onClick = { nav.navigate(Routes.recording(r.id)) })
                }
            }
        }
    }
}

@Composable
private fun RecordPanel(recording: Boolean, finishing: Boolean, onClick: () -> Unit) {
    val startedAt by Recorder.startedAt.collectAsStateWithLifecycle()
    val level by Recorder.level.collectAsStateWithLifecycle()
    val liveWave by Recorder.liveWave.collectAsStateWithLifecycle()
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording, startedAt) {
        while (recording && startedAt > 0) { elapsed = System.currentTimeMillis() - startedAt; delay(200) }
    }
    val pulse by animateFloatAsState(if (recording) 1f + level * 0.18f else 1f, tween(120), label = "pulse")

    SectionCard(
        container = if (recording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (recording) {
                Text(formatClock(elapsed), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Aufnahme läuft …", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(12.dp))
                LiveWaveform(liveWave, Modifier.fillMaxWidth().height(56.dp), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
            } else {
                Text("Tonaufnahme", style = MaterialTheme.typography.titleLarge)
                Text("Startet sofort und läuft im Hintergrund", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
            }
            Box(
                Modifier.size(96.dp).scale(pulse).clip(CircleShape)
                    .background(if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !finishing) { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (recording) "Aufnahme stoppen" else "Aufnahme starten",
                    tint = if (recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
            if (finishing) { Spacer(Modifier.height(8.dp)); Text("Wird gespeichert …", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun RecordingRow(r: Recording, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.GraphicEq, null, tint = MaterialTheme.colorScheme.onTertiaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.displayTitle(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text("${Fmt.dateTime(r.startedAt)} Uhr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(formatClock(r.durationMs), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Outlined.MicNone)
                    if (r.location != null) Pill("Ort", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Outlined.LocationOn)
                }
            }
        }
    }
}
