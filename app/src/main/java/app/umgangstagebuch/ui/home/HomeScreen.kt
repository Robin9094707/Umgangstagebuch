package app.umgangstagebuch.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.data.Moods
import app.umgangstagebuch.data.VisitType
import app.umgangstagebuch.data.nextVisit
import app.umgangstagebuch.data.stats
import app.umgangstagebuch.data.visitsNeedingReport
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.DateBadge
import app.umgangstagebuch.ui.components.EmptyState
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.components.SectionTitle
import app.umgangstagebuch.ui.components.ShapeBadge
import app.umgangstagebuch.ui.journal.EntryCard
import app.umgangstagebuch.ui.switchTab
import app.umgangstagebuch.ui.theme.overline
import app.umgangstagebuch.ui.theme.statusColors
import app.umgangstagebuch.util.Fmt
import java.time.LocalDate

@Composable
fun HomeScreen(nav: NavController) {
    val repo = LocalRepo.current
    val data by repo.data.collectAsStateWithLifecycle()
    val photos by repo.photos.collectAsStateWithLifecycle()
    val p = data.profile
    val now = remember(data) { System.currentTimeMillis() }
    val next = data.nextVisit(now)
    val needing = data.visitsNeedingReport(now)
    val yearStart = Fmt.millis(LocalDate.now().withDayOfYear(1))
    val stats = data.visits.stats(from = yearStart, to = now)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.statusBarsPadding(),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.dateLong(now), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${Fmt.greeting()}, ${p.motherName.ifBlank { "du" }}", style = MaterialTheme.typography.headlineMedium)
                }
                ShapeBadge(shape = MaterialShapes.Cookie9Sided.toShape(), size = 52.dp, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                    Text(p.childDisplay.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Assistent: offene Berichte
        needing.firstOrNull()?.let { v ->
            item {
                SectionCard(container = MaterialTheme.colorScheme.primary) {
                    val on = MaterialTheme.colorScheme.onPrimary
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = on)
                        Spacer(Modifier.width(8.dp))
                        Text("ASSISTENT", style = MaterialTheme.typography.overline, color = on)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Wie war der Umgang ${Fmt.relativeDay(v.end)}?", style = MaterialTheme.typography.headlineSmall, color = on)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Halte in wenigen Schritten fest, wie es ${p.childDisplay} vorher und nachher ging – solange alles noch frisch ist.",
                        style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { nav.navigate(Routes.entryEdit(visitId = v.id, category = EntryCategory.VISIT_REPORT.name, wizard = true)) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary, contentColor = MaterialTheme.colorScheme.primary),
                            shapes = ButtonDefaults.shapes(),
                        ) { Text("Bericht starten"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp)) }
                        TextButton(onClick = { nav.navigate(Routes.visit(v.id)) }, colors = ButtonDefaults.textButtonColors(contentColor = on)) { Text("Status setzen") }
                    }
                    if (needing.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text("Noch ${needing.size - 1} weitere Umgänge ohne Bericht.", style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // Nächster Umgang
        item {
            if (next != null) {
                SectionCard(title = "Nächster Umgang", icon = Icons.Outlined.FamilyRestroom, onClick = { nav.navigate(Routes.visit(next.id)) }, container = MaterialTheme.colorScheme.secondaryContainer) {
                    val fg = MaterialTheme.colorScheme.onSecondaryContainer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DateBadge(next.start, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.relativeDay(next.start).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall, color = fg)
                            val verb = if (next.type == VisitType.PHONE || next.type == VisitType.VIDEO) "${next.type.label} mit" else "bei"
                            Text("${p.childDisplay} $verb ${p.fatherDisplay}", style = MaterialTheme.typography.bodyLarge, color = fg)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    InfoLine(Icons.Outlined.Schedule, Fmt.range(next.start, next.end), fg)
                    if (next.location.isNotBlank()) InfoLine(Icons.Outlined.Place, next.location, fg)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { nav.navigate(Routes.visit(next.id)) }) { Text("Absage erfassen", color = fg) }
                    }
                }
            } else {
                SectionCard(title = "Nächster Umgang", icon = Icons.Outlined.FamilyRestroom) {
                    Text("Kein Umgang geplant.", style = MaterialTheme.typography.titleMedium)
                    Text("Trage den nächsten Termin ein, damit die App dich erinnern kann.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { nav.navigate(Routes.visitEdit()) }, shapes = ButtonDefaults.shapes()) { Text("Umgang planen") }
                }
            }
        }

        // Schnellaktionen
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("Bericht", Icons.Outlined.FamilyRestroom, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f)) {
                    nav.navigate(Routes.entryEdit(category = EntryCategory.VISIT_REPORT.name, wizard = true))
                }
                QuickAction("Notiz", Icons.Outlined.EditNote, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f)) {
                    nav.navigate(Routes.entryEdit())
                }
                QuickAction("Termin", Icons.Outlined.EventAvailable, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f)) {
                    nav.navigate(Routes.visitEdit())
                }
            }
        }

        // Statistik
        item {
            SectionCard(title = "Dieses Jahr", icon = Icons.Outlined.EventBusy) {
                val sc = statusColors
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                        val rel = stats.reliability
                        CircularWavyProgressIndicator(
                            progress = { (rel ?: 0) / 100f },
                            modifier = Modifier.size(96.dp),
                            color = sc.good,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(rel?.let { "$it %" } ?: "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("wahrgen.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatLine("Stattgefunden", stats.done + stats.shortened, sc.good)
                        StatLine("Abgesagt vom Vater", stats.cancelledByFather, sc.bad)
                        StatLine("Nicht erschienen", stats.noShow, sc.bad)
                        StatLine("Andere Absagen", stats.cancelledByMother + stats.cancelledChildIll + stats.cancelledOther, MaterialTheme.colorScheme.outline)
                    }
                }
                if (stats.shortNotice > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text("Davon ${stats.shortNotice}× kurzfristig (weniger als 24 Std. vorher) abgesagt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Stimmungsverlauf
        val reports = data.entries.filter { it.category == EntryCategory.VISIT_REPORT && (it.moodBefore != null || it.moodAfter != null) }
            .sortedBy { it.eventDate }.takeLast(10)
        if (reports.size >= 2) {
            item {
                SectionCard(title = "Stimmung von ${p.childDisplay}") {
                    MoodChart(reports.map { it.moodBefore }, reports.map { it.moodAfter })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend("vorher", MaterialTheme.colorScheme.tertiary)
                        Legend("nachher", MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        // Letzte Einträge
        item {
            SectionTitle("Letzte Einträge") {
                TextButton(onClick = { nav.switchTab(Routes.JOURNAL) }) { Text("Alle") }
            }
        }
        val recent = data.entries.sortedByDescending { it.eventDate }.take(3)
        if (recent.isEmpty()) {
            item {
                EmptyState("✍️", "Noch keine Einträge", "Dein erster Eintrag ist nur einen Fingertipp entfernt.")
            }
        } else {
            items(recent, key = { it.id }) { e ->
                EntryCard(e, p.childDisplay, photos, onClick = { nav.navigate(Routes.entry(e.id)) })
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(96.dp), shape = MaterialTheme.shapes.large, color = bg, contentColor = fg) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, Modifier.size(28.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StatLine(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 16.dp, height = 4.dp)) { drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)) }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MoodChart(before: List<Int?>, after: List<Int?>) {
    val c1 = MaterialTheme.colorScheme.tertiary
    val c2 = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row {
        Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Moods.emoji.reversed().forEach { Text(it, fontSize = 14.sp) }
        }
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.weight(1f).height(140.dp).padding(vertical = 8.dp)) {
            val n = maxOf(before.size, 2)
            fun x(i: Int) = size.width * i / (n - 1)
            fun y(v: Int) = size.height * (5 - v) / 4f
            for (v in 1..5) drawLine(grid, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1f)
            listOf(before to c1, after to c2).forEach { (values, color) ->
                val path = Path()
                var started = false
                values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val pt = Offset(x(i), y(v))
                    if (!started) { path.moveTo(pt.x, pt.y); started = true } else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, color, style = Stroke(width = 6f, cap = StrokeCap.Round))
                values.forEachIndexed { i, v -> if (v != null) drawCircle(color, 9f, Offset(x(i), y(v))) }
            }
        }
    }
}
