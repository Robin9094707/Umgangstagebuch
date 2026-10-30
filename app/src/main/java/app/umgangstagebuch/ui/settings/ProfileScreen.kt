package app.umgangstagebuch.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.umgangstagebuch.ui.components.AppTextField
import app.umgangstagebuch.ui.components.DatePickerField
import app.umgangstagebuch.ui.components.LocalRepo
import app.umgangstagebuch.ui.components.OutlinedInfo
import app.umgangstagebuch.ui.components.SectionCard
import app.umgangstagebuch.ui.onboarding.EmojiIdeas
import app.umgangstagebuch.ui.onboarding.NicknameIdeas
import app.umgangstagebuch.util.Fmt
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(nav: NavController) {
    val repo = LocalRepo.current
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(repo.data.value.profile) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = "Du") {
                AppTextField(p.motherName, { p = p.copy(motherName = it) }, "Dein Vorname", singleLine = true, capitalization = KeyboardCapitalization.Words)
            }
            SectionCard(title = "Kind") {
                AppTextField(p.childName, { p = p.copy(childName = it) }, "Name", singleLine = true, capitalization = KeyboardCapitalization.Words)
                Spacer(Modifier.height(10.dp))
                DatePickerField("Geburtstag", p.childBirthday?.let { Fmt.pickerToDate(it) }, { p = p.copy(childBirthday = Fmt.dateToPicker(it)) }, Modifier.fillMaxWidth(), placeholder = "Nicht angegeben")
                Spacer(Modifier.height(10.dp))
                AppTextField(p.childDetails, { p = p.copy(childDetails = it) }, "Details", minLines = 3, placeholder = "Kita, Allergien, Kinderarzt …")
            }
            SectionCard(title = "Vater") {
                AppTextField(p.fatherName, { p = p.copy(fatherName = it) }, "Echter Name (für Berichte)", singleLine = true, capitalization = KeyboardCapitalization.Words)
                Spacer(Modifier.height(10.dp))
                AppTextField(p.fatherNickname, { p = p.copy(fatherNickname = it) }, "Spitzname in der App", singleLine = true)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NicknameIdeas.forEach { n -> FilterChip(selected = p.fatherNickname == n, onClick = { p = p.copy(fatherNickname = n) }, label = { Text(n) }, shape = CircleShape) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EmojiIdeas.forEach { e ->
                        FilterChip(selected = p.fatherEmoji == e, onClick = { p = p.copy(fatherEmoji = if (p.fatherEmoji == e) "" else e) }, label = { Text(e, fontSize = 20.sp) }, shape = CircleShape)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Spitzname anzeigen", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(p.useNickname, { p = p.copy(useNickname = it) })
                }
                Text("In der App: ${p.fatherDisplay}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedInfo("In PDF-Berichten steht immer: ${p.fatherOfficial}", icon = Icons.Outlined.Verified)
            }
            SectionCard(title = "Standard") {
                AppTextField(p.defaultLocation, { p = p.copy(defaultLocation = it) }, "Üblicher Übergabeort", singleLine = true, leading = Icons.Outlined.Place)
            }
            Button(
                onClick = { scope.launch { repo.updateProfile(p); repo.message("Profil gespeichert"); nav.popBackStack() } },
                enabled = p.motherName.isNotBlank() && p.childName.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Speichern") }
        }
    }
}
