package app.umgangstagebuch.ui.journal

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.umgangstagebuch.data.Entry
import app.umgangstagebuch.data.EntryCategory
import app.umgangstagebuch.data.Moods
import app.umgangstagebuch.data.Tags
import app.umgangstagebuch.ui.components.IconBadge
import app.umgangstagebuch.ui.components.Pill
import app.umgangstagebuch.ui.theme.statusColors
import app.umgangstagebuch.util.Fmt
import coil3.compose.AsyncImage

val EntryCategory.icon: ImageVector
    get() = when (this) {
        EntryCategory.VISIT_REPORT -> Icons.Outlined.FamilyRestroom
        EntryCategory.HANDOVER -> Icons.Outlined.SwapHoriz
        EntryCategory.CHILD -> Icons.Outlined.ChildCare
        EntryCategory.COMMUNICATION -> Icons.Outlined.ChatBubbleOutline
        EntryCategory.HEALTH -> Icons.Outlined.HealthAndSafety
        EntryCategory.SCHOOL -> Icons.Outlined.School
        EntryCategory.OFFICIAL -> Icons.Outlined.Gavel
        EntryCategory.OTHER -> Icons.Outlined.EditNote
    }

@Composable
fun EntryCategory.colors(): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (this) {
        EntryCategory.VISIT_REPORT -> c.primaryContainer to c.onPrimaryContainer
        EntryCategory.HANDOVER, EntryCategory.COMMUNICATION -> c.secondaryContainer to c.onSecondaryContainer
        EntryCategory.CHILD, EntryCategory.HEALTH -> c.tertiaryContainer to c.onTertiaryContainer
        else -> c.surfaceContainerHighest to c.onSurfaceVariant
    }
}

fun Entry.displayTitle(childName: String): String = title.ifBlank {
    when (category) {
        EntryCategory.VISIT_REPORT -> "Umgang am ${Fmt.date(eventDate)}"
        EntryCategory.CHILD -> "Wie es $childName ging"
        else -> category.label
    }
}

fun Entry.preview(): String = listOf(text, childQuotes, pickup?.note.orEmpty(), dropoff?.note.orEmpty(), health)
    .firstOrNull { it.isNotBlank() }?.replace('\n', ' ').orEmpty()

@Composable
fun EntryCard(entry: Entry, childName: String, photos: Map<String, Uri>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (bg, fg) = entry.category.colors()
    val sc = statusColors
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(entry.category.icon, color = bg, contentColor = fg, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.displayTitle(childName), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Fmt.dateTime(entry.eventDate)} · ${entry.category.short}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entry.moodBefore != null || entry.moodAfter != null) {
                    Text(
                        listOfNotNull(entry.moodBefore?.let { Moods.emojiOf(it) }, entry.moodAfter?.let { Moods.emojiOf(it) }).joinToString(" → "),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            val preview = entry.preview()
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(preview, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val concerning = (entry.tags + entry.tagsBefore).filter { it in Tags.concerning }.distinct()
            if (concerning.isNotEmpty() || entry.photos.isNotEmpty() || entry.isEdited) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    concerning.take(2).forEach { Pill(it, sc.warnContainer, sc.onWarnContainer) }
                    if (concerning.size > 2) Pill("+${concerning.size - 2}", sc.warnContainer, sc.onWarnContainer)
                    if (entry.photos.isNotEmpty()) Pill("${entry.photos.size}", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Outlined.Photo)
                    if (entry.isEdited) Pill("bearbeitet", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            entry.photos.firstOrNull()?.let { p ->
                photos[p.fileName]?.let { uri ->
                    Spacer(Modifier.height(10.dp))
                    AsyncImage(
                        model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(140.dp).clip(MaterialTheme.shapes.medium),
                    )
                }
            }
        }
    }
}

