package app.umgangstagebuch.ui.visits

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.Profile
import app.umgangstagebuch.data.Visit
import app.umgangstagebuch.data.VisitStatus
import app.umgangstagebuch.data.stats
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.DateBadge
import app.umgangstagebuch.ui.components.EmptyState
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.Pill
import app.umgangstagebuch.ui.components.VisitStatusPill
import app.umgangstagebuch.ui.theme.statusColors
import app.umgangstagebuch.util.Fmt

@Composable
fun VisitsScreen(nav: NavController) {
    val repo = LocalRepo.current
    val data by repo.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val now = remember(data) { System.currentTimeMillis() }
    val upcoming = data.visits.filter { it.end >= now }.sortedBy { it.start }
    val past = data.visits.filter { it.end < now }.sortedByDescending { it.start }
    val reported = data.entries.mapNotNull { it.visitId }.toSet()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Umgänge") },
                subtitle = {
                    val s = data.visits.stats(now = now)
                    Text("${s.done + s.shortened} stattgefunden · ${s.fatherMissed} vom Vater versäumt")
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.visitEdit()) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Umgang planen") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Anstehend (${upcoming.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Vergangen (${past.size})") })
            }
            val list = if (tab == 0) upcoming else past
            if (list.isEmpty()) {
                EmptyState(
                    if (tab == 0) "📅" else "🗂️",
                    if (tab == 0) "Keine Termine geplant" else "Noch keine vergangenen Umgänge",
                    if (tab == 0) "Lege den nächsten Umgang oder gleich die ganze Umgangsregelung an." else "Hier erscheinen Umgänge, sobald sie vorbei sind.",
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    var lastMonth = ""
                    list.forEach { v ->
                        val m = Fmt.month(v.start)
                        if (m != lastMonth) {
                            lastMonth = m
                            item(key = "h-$m-${v.id}") {
                                Text(m, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                            }
                        }
                        item(key = v.id) {
                            VisitRow(v, data.profile, v.id in reported) { nav.navigate(Routes.visit(v.id)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisitRow(v: Visit, profile: Profile, hasReport: Boolean, onClick: () -> Unit) {
    val sc = statusColors
    val cancelled = v.status == VisitStatus.CANCELLED || v.status == VisitStatus.NO_SHOW
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DateBadge(
                v.start,
                color = if (cancelled) sc.badContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (cancelled) sc.onBadContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(v.type.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Fmt.time(v.start)} – ${if (Fmt.ld(v.start) == Fmt.ld(v.end)) Fmt.time(v.end) else Fmt.date(v.end) + ", " + Fmt.time(v.end)} Uhr" +
                        (v.location.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VisitStatusPill(v)
                    if (hasReport) Pill("Bericht", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, icon = Icons.Outlined.Description)
                }
            }
        }
    }
}
