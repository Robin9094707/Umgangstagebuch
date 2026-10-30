package app.umgangstagebuch.ui.onboarding

import android.Manifest
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.umgangstagebuch.data.AppData
import app.umgangstagebuch.data.Profile
import app.umgangstagebuch.data.Repeat
import app.umgangstagebuch.ui.components.AppTextField
import app.umgangstagebuch.ui.components.DatePickerField
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.OutlinedInfo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.components.ShapeBadge
import app.umgangstagebuch.ui.visits.ScheduleEditor
import app.umgangstagebuch.ui.visits.ScheduleState
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch

private enum class Step { WELCOME, FOLDER, MOTHER, CHILD, FATHER, PLAN, PERMISSIONS, DONE }

val NicknameIdeas = listOf("Stinktier", "Erzeuger", "Der Herr", "Kindsvater", "Mister X", "Papa-Bär", "Er", "Der Ex")
val EmojiIdeas = listOf("🦨", "🐍", "🦖", "🤡", "🐌", "🙄", "👻", "🧔")

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(Step.WELCOME) }
    var mother by rememberSaveable { mutableStateOf("") }
    var child by rememberSaveable { mutableStateOf("") }
    var childBirthday by rememberSaveable { mutableStateOf<Long?>(null) }
    var childDetails by rememberSaveable { mutableStateOf("") }
    var father by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("") }
    var useNickname by rememberSaveable { mutableStateOf(true) }
    var folderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<AppData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }
    var appLock by rememberSaveable { mutableStateOf(false) }
    val plan = remember { ScheduleState(repeat = Repeat.BIWEEKLY) }
    var planEnabled by rememberSaveable { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        folderError = null
        scope.launch {
            repo.connectFolder(uri)
                .onSuccess { data ->
                    folderPath = repo.storage.displayPath(uri)
                    existing = data
                    data?.profile?.let { p ->
                        mother = p.motherName; child = p.childName; childBirthday = p.childBirthday
                        childDetails = p.childDetails; father = p.fatherName; nickname = p.fatherNickname
                        emoji = p.fatherEmoji; useNickname = p.useNickname
                    }
                }
                .onFailure { folderError = "Dieser Ordner kann nicht verwendet werden. Bitte wähle einen anderen Ordner (z. B. in „Documents“)." }
            busy = false
        }
    }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAsked = true }

    val order = Step.entries
    fun next() {
        step = when {
            step == Step.FOLDER && existing?.profile?.isComplete == true -> Step.PERMISSIONS
            else -> order[(order.indexOf(step) + 1).coerceAtMost(order.lastIndex)]
        }
    }
    fun back() {
        step = when {
            step == Step.PERMISSIONS && existing?.profile?.isComplete == true -> Step.FOLDER
            else -> order[(order.indexOf(step) - 1).coerceAtLeast(0)]
        }
    }
    BackHandler(enabled = step != Step.WELCOME && step != Step.DONE) { back() }

    fun finish() {
        busy = true
        scope.launch {
            val base = existing ?: AppData()
            val profile = Profile(
                motherName = mother.trim(), childName = child.trim(), childBirthday = childBirthday,
                childDetails = childDetails.trim(), fatherName = father.trim(), fatherNickname = nickname.trim(),
                fatherEmoji = emoji, useNickname = useNickname, defaultLocation = plan.location.trim(),
                createdAt = base.profile.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
            val visits = if (planEnabled && existing == null) plan.build() else emptyList()
            repo.prefs.update { it.copy(appLock = appLock) }
            repo.completeSetup(base.copy(profile = profile, visits = (base.visits + visits).sortedBy { it.start }))
            busy = false
            step = Step.DONE
        }
    }

    val canContinue = when (step) {
        Step.FOLDER -> folderPath != null && !busy
        Step.MOTHER -> mother.isNotBlank()
        Step.CHILD -> child.isNotBlank()
        Step.PLAN -> !planEnabled || plan.isValid
        else -> true
    }
    val progress by animateFloatAsState(order.indexOf(step) / (order.size - 1f), label = "progress")

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (step != Step.WELCOME && step != Step.DONE) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                    LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).padding(end = 16.dp))
                }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (s) {
                        Step.WELCOME -> Welcome()
                        Step.FOLDER -> FolderStep(folderPath, existing, busy, folderError) {
                            folderPicker.launch(suggestedFolder())
                        }
                        Step.MOTHER -> {
                            StepHeader("👋", "Wie heißt du?", "So kann dich die App persönlich begrüßen.")
                            AppTextField(mother, { mother = it }, "Dein Vorname", singleLine = true, capitalization = KeyboardCapitalization.Words)
                        }
                        Step.CHILD -> {
                            StepHeader("🧒", "Um wen geht es?", "Ein paar Angaben zu deinem Kind. Alles außer dem Namen ist freiwillig.")
                            AppTextField(child, { child = it }, "Name des Kindes", singleLine = true, capitalization = KeyboardCapitalization.Words, leading = Icons.Outlined.Badge)
                            DatePickerField(
                                "Geburtstag (optional)",
                                childBirthday?.let { Fmt.pickerToDate(it) },
                                { childBirthday = Fmt.dateToPicker(it) },
                                Modifier.fillMaxWidth(),
                                placeholder = "Nicht angegeben",
                            )
                            AppTextField(
                                childDetails, { childDetails = it }, "Details (optional)", minLines = 3,
                                placeholder = "z. B. Kita-Gruppe, Allergien, Kinderarzt, Besonderheiten",
                            )
                        }
                        Step.FATHER -> {
                            StepHeader("🦨", "Und der Vater?", "Der echte Name wird nur in offiziellen Berichten verwendet. In der App darfst du ihn nennen, wie du möchtest.")
                            AppTextField(father, { father = it }, "Echter Name (für Berichte)", singleLine = true, capitalization = KeyboardCapitalization.Words)
                            AppTextField(nickname, { nickname = it }, "Spitzname in der App", singleLine = true, placeholder = "z. B. Stinktier")
                            Text("Ideen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NicknameIdeas.forEach { n -> FilterChip(selected = nickname == n, onClick = { nickname = n }, label = { Text(n) }, shape = CircleShape) }
                            }
                            Text("Passendes Emoji", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EmojiIdeas.forEach { e ->
                                    FilterChip(selected = emoji == e, onClick = { emoji = if (emoji == e) "" else e }, label = { Text(e, fontSize = 20.sp) }, shape = CircleShape)
                                }
                            }
                            SwitchRow("Spitzname in der App anzeigen", useNickname) { useNickname = it }
                            val preview = Profile(fatherName = father, fatherNickname = nickname, fatherEmoji = emoji, useNickname = useNickname, childName = child)
                            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("Vorschau", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.height(4.dp))
                                Text("„${preview.childDisplay} ist am Samstag bei ${preview.fatherDisplay}.“", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            OutlinedInfo("Im PDF-Bericht für Jugendamt, Anwalt oder Gericht steht immer sachlich der echte Name.", icon = Icons.Outlined.Verified)
                        }
                        Step.PLAN -> {
                            StepHeader("📅", "Gibt es feste Umgangszeiten?", "Lege die Umgangsregelung an – die App erinnert dich dann vorher und fragt danach, wie es war.")
                            SwitchRow("Regelmäßige Umgänge jetzt eintragen", planEnabled) { planEnabled = it }
                            AnimatedVisibility(planEnabled) {
                                SectionCard { ScheduleEditor(plan, allowRepeat = true, showNote = false) }
                            }
                            if (!planEnabled) OutlinedInfo("Kein Problem – du kannst Termine auch später jederzeit unter „Umgänge“ eintragen.", icon = Icons.Outlined.Info)
                        }
                        Step.PERMISSIONS -> {
                            if (existing?.profile?.isComplete == true) {
                                StepHeader("💜", "Willkommen zurück, ${existing!!.profile.motherName}!", "Deine Daten wurden gefunden und werden weiterverwendet.")
                            } else {
                                StepHeader("🔔", "Erinnerungen & Schutz", "Damit du nichts vergisst – und niemand ungefragt hineinschaut.")
                            }
                            SectionCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Benachrichtigungen", style = MaterialTheme.typography.titleMedium)
                                        Text("Erinnerung vor jedem Umgang und danach eine sanfte Frage, wie es war.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                if (Build.VERSION.SDK_INT >= 33) {
                                    FilledTonalButton(onClick = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, enabled = !notificationsAsked) {
                                        Text(if (notificationsAsked) "Erledigt" else "Benachrichtigungen erlauben")
                                    }
                                } else {
                                    Text("Bereits aktiv.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            SectionCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("App-Sperre", style = MaterialTheme.typography.titleMedium)
                                        Text("Beim Öffnen mit Fingerabdruck, Gesicht oder PIN entsperren.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(appLock, { appLock = it })
                                }
                            }
                        }
                        Step.DONE -> Done(mother)
                    }
                }
            }

            // Untere Aktionsleiste
            Box(Modifier.fillMaxWidth().padding(24.dp)) {
                when (step) {
                    Step.WELCOME -> BigButton("Los geht's", Icons.AutoMirrored.Rounded.ArrowForward) { next() }
                    Step.PERMISSIONS -> BigButton(if (busy) "Wird eingerichtet …" else "Einrichtung abschließen", Icons.Outlined.CheckCircle, enabled = !busy) { finish() }
                    Step.DONE -> BigButton("Zum Tagebuch", Icons.AutoMirrored.Rounded.ArrowForward) { onFinished() }
                    else -> BigButton("Weiter", Icons.AutoMirrored.Rounded.ArrowForward, enabled = canContinue) { next() }
                }
            }
        }
    }
}

private fun suggestedFolder(): Uri? = runCatching {
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
}.getOrNull()

@Composable
private fun BigButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Icon(icon, null)
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(onClick = { onChange(!checked) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked, onChange)
        }
    }
}

@Composable
fun StepHeader(emoji: String, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        ShapeBadge(shape = MaterialShapes.Cookie9Sided.toShape(), size = 72.dp, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(emoji, fontSize = 32.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.Welcome() {
    val t = rememberInfiniteTransition(label = "welcome")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.96f, 1.04f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse")
    Spacer(Modifier.height(32.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(220.dp).rotate(rot),
            shape = MaterialShapes.Cookie12Sided.toShape(),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {}
        Surface(
            modifier = Modifier.size(150.dp).scale(pulse),
            shape = MaterialShapes.Flower.toShape(),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {}
        Text("📖", fontSize = 64.sp)
    }
    Spacer(Modifier.height(24.dp))
    Text("Dein Umgangstagebuch", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(
        "Halte fest, wie die Umgänge laufen und wie es deinem Kind dabei geht – mit Datum, Fotos und sicher auf deinem Handy.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Feature(Icons.Outlined.Verified, "Beweissicher", "Jeder Eintrag bekommt automatisch Datum, Uhrzeit und eine Prüfsumme.")
    Feature(Icons.Outlined.CloudOff, "Privat", "Keine Cloud, kein Konto. Deine Daten bleiben in deinem Ordner.")
    Feature(Icons.Outlined.NotificationsActive, "Begleitend", "Erinnert an Termine und fragt danach, wie es war.")
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ShapeBadge(size = 44.dp, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
            Icon(icon, null, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FolderStep(path: String?, existing: AppData?, busy: Boolean, error: String?, onPick: () -> Unit) {
    StepHeader("📁", "Wo sollen deine Daten liegen?", "Wähle einen Ordner auf deinem Handy. Dort speichert die App alles – auch die Fotos.")
    Feature(Icons.Outlined.Lock, "Bleibt erhalten", "Selbst wenn die App gelöscht wird, sind deine Einträge noch da.")
    Feature(Icons.Outlined.Restore, "Wiederherstellen", "Nach einer Neuinstallation einfach denselben Ordner wählen.")
    OutlinedInfo(
        "Tipp: Lege im Ordner „Documents“ einen neuen Ordner „Umgangstagebuch“ an (Symbol mit dem Plus oben rechts) und tippe dann auf „Diesen Ordner verwenden“.",
        icon = Icons.Outlined.Info,
    )
    if (busy) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    } else {
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) {
            Icon(if (path == null) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null)
            Spacer(Modifier.width(8.dp))
            Text(if (path == null) "Ordner auswählen" else "Anderen Ordner wählen")
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (path != null) {
        SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Speicherort", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(path, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (existing != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Hier liegt schon ein Tagebuch mit ${existing.entries.size} Einträgen und ${existing.visits.size} Umgängen. Es wird übernommen.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Done(name: String) {
    val t = rememberInfiniteTransition(label = "done")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "rot")
    var shown by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(if (shown == 1) 1f else 0.4f, label = "scale")
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = 1 }
    Spacer(Modifier.height(48.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(Modifier.size(200.dp).rotate(rot).scale(scale), shape = MaterialShapes.Sunny.toShape(), color = MaterialTheme.colorScheme.tertiaryContainer) {}
        Text("🎉", fontSize = 72.sp, modifier = Modifier.scale(scale))
    }
    Spacer(Modifier.height(24.dp))
    Text("Alles bereit${if (name.isNotBlank()) ", $name" else ""}!", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Text(
        "Du kannst jetzt Umgänge planen, Absagen festhalten und nach jedem Umgang aufschreiben, wie es war. Du machst das großartig. 💜",
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

