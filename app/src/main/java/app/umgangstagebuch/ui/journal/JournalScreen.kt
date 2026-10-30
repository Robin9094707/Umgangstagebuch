package app.umgangstagebuch.ui.journal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.ui.Routes
import app.umgangstagebuch.ui.components.EmptyState
import app.umgangstagebuch.ui.components.ListRow
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.util.Fmt

@Composable
fun JournalScreen(nav: NavController) {
    val repo = LocalRepo.current
    val data by repo.data.collectAsStateWithLifecycle()
    val photos by repo.photos.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<EntryCategory?>(null) }
    var chooser by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val child = data.profile.childDisplay

    val list = data.entries
        .filter { filter == null || it.category == filter }
        .filter { e ->
            query.isBlank() || listOf(e.title, e.text, e.childQuotes, e.health, e.witnesses, e.pickup?.note, e.dropoff?.note, e.category.label)
                .plus(e.tags).plus(e.tagsBefore).any { it?.contains(query.trim(), ignoreCase = true) == true }
        }
        .sortedByDescending { it.eventDate }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Tagebuch") },
                subtitle = { Text("${data.entries.size} Einträge über $child") },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { chooser = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Neuer Eintrag") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Suchen …") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Leeren") } },
                    singleLine = true,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Alle") }, shape = CircleShape)
                    EntryCategory.entries.forEach { c ->
                        val count = data.entries.count { it.category == c }
                        if (count > 0) {
                            FilterChip(
                                selected = filter == c,
                                onClick = { filter = if (filter == c) null else c },
                                label = { Text("${c.short} ($count)") },
                                leadingIcon = { Icon(if (filter == c) Icons.Rounded.Check else c.icon, null, Modifier.size(18.dp)) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    if (data.entries.isEmpty()) {
                        EmptyState("📖", "Dein Tagebuch ist noch leer", "Halte fest, wie es $child geht – jeder Eintrag bekommt automatisch Datum und Uhrzeit.")
                    } else {
                        EmptyState("🔍", "Nichts gefunden", "Versuche einen anderen Suchbegriff oder Filter.")
                    }
                }
            }
            var lastMonth = ""
            list.forEach { e ->
                val m = Fmt.month(e.eventDate)
                if (m != lastMonth) {
                    lastMonth = m
                    item(key = "m-$m-${e.id}") {
                        Text(m, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    }
                }
                item(key = e.id) { EntryCard(e, child, photos, onClick = { nav.navigate(Routes.entry(e.id)) }) }
            }
        }
    }

    if (chooser) {
        ModalBottomSheet(onDismissRequest = { chooser = false }) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Text("Was möchtest du festhalten?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                ListRow(
                    "Umgangsbericht mit Assistent",
                    subtitle = "Schritt für Schritt: vorher, Übergabe, nachher",
                    icon = Icons.Outlined.AutoAwesome,
                    iconColor = MaterialTheme.colorScheme.primary,
                    iconContent = MaterialTheme.colorScheme.onPrimary,
                    onClick = { chooser = false; nav.navigate(Routes.entryEdit(category = EntryCategory.VISIT_REPORT.name, wizard = true)) },
                )
                Spacer(Modifier.height(4.dp))
                EntryCategory.entries.forEach { c ->
                    val (bg, fg) = c.colors()
                    ListRow(c.label, icon = c.icon, iconColor = bg, iconContent = fg, trailing = null, onClick = {
                        chooser = false
                        nav.navigate(Routes.entryEdit(category = c.name))
                    })
                }
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
