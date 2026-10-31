package app.umgangstagebuch.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.BuildConfigCompat
import app.umgangstagebuch.data.BackupPreview
import app.umgangstagebuch.data.ImportMode
import app.umgangstagebuch.data.ThemeMode
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.ChoiceChips
import app.umgangstagebuch.ui.components.ListRow
import app.umgangstagebuch.ui.components.LocalApp
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.components.ShapeBadge
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(nav: NavController) {
    val app = LocalApp.current
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val data by repo.data.collectAsStateWithLifecycle()
    val settings by repo.prefs.settings.collectAsStateWithLifecycle()
    val p = data.profile
    var busy by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        busy = "Backup wird erstellt …"
        scope.launch {
            runCatching { app.backup.export(uri) }
                .onSuccess { repo.message("Backup gespeichert ✓") }
                .onFailure { repo.message("Backup fehlgeschlagen: ${it.message}") }
            busy = null
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        busy = "Backup wird gelesen …"
        scope.launch {
            runCatching { app.backup.read(uri) }
                .onSuccess { preview = it }
                .onFailure { repo.message("Diese Datei ist kein gültiges Backup: ${it.message}") }
            busy = null
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        busy = "Daten werden kopiert …"
        scope.launch {
            repo.moveToFolder(uri)
                .onSuccess { repo.message("Neuer Speicherort: ${repo.storage.displayPath(uri)}") }
                .onFailure { repo.message(it.message ?: "Ordnerwechsel fehlgeschlagen") }
            busy = null
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun updateSettings(block: (app.umgangstagebuch.data.Settings) -> app.umgangstagebuch.data.Settings) {
        repo.prefs.update(block)
        repo.rescheduleReminders()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text("Mehr") }, scrollBehavior = scroll) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(onClick = { nav.navigate(Routes.PROFILE) }, container = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(shape = MaterialShapes.Cookie9Sided.toShape(), size = 64.dp, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text(p.motherName.take(1).uppercase().ifBlank { "M" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.motherName.ifBlank { "Profil" }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Mama von ${p.childDisplay}" + (Fmt.age(p.childBirthday)?.let { " ($it)" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text("Vater: ${p.fatherDisplay}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text("Bearbeiten", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionCard(title = "Berichte & Sicherung", contentPadding = PaddingValues(12.dp)) {
                ListRow("PDF-Bericht erstellen", subtitle = "Für Jugendamt, Anwalt oder Gericht", icon = Icons.Outlined.PictureAsPdf, onClick = { nav.navigate(Routes.REPORT) })
                ListRow("Backup exportieren", subtitle = "Alle Daten, Fotos & Aufnahmen als ZIP-Datei", icon = Icons.Outlined.Backup, onClick = {
                    exportLauncher.launch("Umgangstagebuch-Backup_${Fmt.fileStamp()}.zip")
                })
                ListRow("Backup importieren", subtitle = "Aus einer ZIP- oder JSON-Datei", icon = Icons.Outlined.Restore, onClick = {
                    importLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "application/x-zip-compressed"))
                })
            }

            SectionCard(title = "Speicherort", icon = Icons.Outlined.Folder) {
                Text(repo.folder?.let { repo.storage.displayPath(it) } ?: "–", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Hier liegen umgangstagebuch.json, der Ordner „fotos“ und tägliche automatische Sicherungen („sicherungen“). " +
                        "Nach einer Neuinstallation einfach diesen Ordner wieder auswählen.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                ListRow("Ordner wechseln", subtitle = "Daten werden in den neuen, leeren Ordner kopiert", icon = Icons.AutoMirrored.Outlined.DriveFileMove, onClick = { folderLauncher.launch(null) })
            }

            SectionCard(title = "Erinnerungen", icon = Icons.Outlined.NotificationsActive) {
                SwitchLine("Vor jedem Umgang erinnern", Icons.Outlined.Event, settings.visitReminders) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    updateSettings { it.copy(visitReminders = v) }
                }
                if (settings.visitReminders) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 12, 24, 48).forEach { h ->
                            ToggleButton(
                                checked = settings.reminderHoursBefore == h,
                                onCheckedChange = { updateSettings { it.copy(reminderHoursBefore = h) } },
                                modifier = Modifier.weight(1f),
                            ) { Text("$h Std.") }
                        }
                    }
                }
                SwitchLine("Nach dem Umgang nach Bericht fragen", Icons.Outlined.RateReview, settings.afterVisitReminder) { v ->
                    updateSettings { it.copy(afterVisitReminder = v) }
                }
                SwitchLine("Wöchentlicher Rückblick (So. 19:30)", Icons.Outlined.Weekend, settings.weeklyReminder) { v ->
                    if (v && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    updateSettings { it.copy(weeklyReminder = v) }
                }
            }

            SectionCard(title = "Sicherheit & Design", icon = Icons.Outlined.Shield) {
                SwitchLine("App-Sperre (Fingerabdruck / PIN)", Icons.Outlined.Fingerprint, settings.appLock) { v -> repo.prefs.update { it.copy(appLock = v) } }
                if (Build.VERSION.SDK_INT >= 31) {
                    SwitchLine("Farben vom Hintergrundbild", Icons.Outlined.Palette, settings.dynamicColor) { v -> repo.prefs.update { it.copy(dynamicColor = v) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Outlined.DarkMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    ChoiceChips(ThemeMode.entries, settings.themeMode, { it.label }, { m -> repo.prefs.update { it.copy(themeMode = m) } })
                }
            }

            SectionCard(title = "Über") {
                Text("Umgangstagebuch ${BuildConfigCompat.versionName(app)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Keine Cloud, kein Konto, keine Werbung. Alle Daten bleiben ausschließlich auf deinem Gerät in deinem Ordner.\n\n" +
                        "Die App ersetzt keine Rechtsberatung. Tipp: Möglichst zeitnah, sachlich und wörtlich dokumentieren.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            icon = { LoadingIndicator() },
            title = { Text(msg) },
        )
    }

    preview?.let { pv ->
        AlertDialog(
            onDismissRequest = { preview = null },
            icon = { Icon(Icons.Outlined.Restore, null) },
            title = { Text("Backup importieren") },
            text = {
                Text(
                    "Gefunden: ${pv.entryCount} Einträge, ${pv.visitCount} Umgänge, ${pv.photoCount} Fotos, ${pv.recordingCount} Aufnahmen" +
                        (pv.data.profile.childName.takeIf { it.isNotBlank() }?.let { " (Kind: $it)" } ?: "") + ".\n\n" +
                        "„Zusammenführen“ ergänzt fehlende Einträge und behält deine aktuellen Daten. " +
                        "„Ersetzen“ überschreibt alles. Vorher wird automatisch eine Sicherung im Ordner „sicherungen“ angelegt.",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        val chosen = pv; preview = null; busy = "Wird importiert …"
                        scope.launch {
                            runCatching { app.backup.apply(chosen, ImportMode.MERGE) }
                                .onSuccess { repo.message("Backup zusammengeführt ✓") }.onFailure { repo.message("Import fehlgeschlagen: ${it.message}") }
                            busy = null
                        }
                    }) { Text("Zusammenführen") }
                    TextButton(onClick = {
                        val chosen = pv; preview = null; busy = "Wird importiert …"
                        scope.launch {
                            runCatching { app.backup.apply(chosen, ImportMode.REPLACE) }
                                .onSuccess { repo.message("Daten ersetzt ✓") }.onFailure { repo.message("Import fehlgeschlagen: ${it.message}") }
                            busy = null
                        }
                    }) { Text("Alles ersetzen", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SwitchLine(text: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
