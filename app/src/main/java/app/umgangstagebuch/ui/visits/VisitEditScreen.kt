package app.umgangstagebuch.ui.visits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun VisitEditScreen(id: String?, nav: NavController) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    val existing = remember(id) { id?.let { i -> repo.data.value.visits.firstOrNull { it.id == i } } }
    val state = remember(id) {
        existing?.let { ScheduleState.from(it) } ?: ScheduleState(location = repo.data.value.profile.defaultLocation)
    }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Umgang planen" else "Umgang bearbeiten") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    saving = true
                    scope.launch {
                        if (existing != null) {
                            repo.saveVisit(
                                existing.copy(
                                    start = Fmt.millis(state.start), end = Fmt.millis(state.end), type = state.type,
                                    location = state.location.trim(), note = state.note.trim(),
                                ),
                            )
                        } else {
                            repo.saveVisits(state.build())
                        }
                        nav.popBackStack()
                    }
                },
                enabled = state.isValid && !saving,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(16.dp).navigationBarsPadding().imePadding().fillMaxWidth().height(56.dp),
            ) { Text(if (existing == null && state.repeat.weeks > 0) "Termine anlegen" else "Speichern") }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard { ScheduleEditor(state, allowRepeat = existing == null) }
        }
    }
}

