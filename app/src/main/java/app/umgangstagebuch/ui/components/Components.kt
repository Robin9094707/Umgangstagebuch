package app.umgangstagebuch.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import app.umgangstagebuch.data.Moods
import app.umgangstagebuch.data.Tags
import app.umgangstagebuch.data.Visit
import app.umgangstagebuch.data.VisitStatus
import app.umgangstagebuch.data.CancelledBy
import app.umgangstagebuch.ui.theme.overline
import app.umgangstagebuch.ui.theme.statusColors
import app.umgangstagebuch.util.Fmt
import java.time.LocalDate
import java.time.LocalTime

/** Abgerundete Karte im Stil von Material 3 Expressive. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title.uppercase(), style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.primary)
                }
            }
            content()
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = container, content = body)
    else Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = container, content = body)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Expressive-Form mit Icon oder Text (z. B. Avatar, Datums-Badge). */
@Composable
fun ShapeBadge(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialShapes.Cookie9Sided.toShape(),
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier.size(size), shape = shape, color = color, contentColor = contentColor) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, shape: Shape = MaterialShapes.Cookie9Sided.toShape(), size: Dp = 44.dp, color: Color = MaterialTheme.colorScheme.primaryContainer, contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    ShapeBadge(modifier, shape, size, color, contentColor) { Icon(icon, null, modifier = Modifier.size(size * 0.48f)) }
}

/** Kalenderblatt: Wochentag + Tag. */
@Composable
fun DateBadge(millis: Long, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondaryContainer, contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(modifier.size(width = 56.dp, height = 60.dp), shape = MaterialTheme.shapes.medium, color = color, contentColor = contentColor) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(Fmt.weekdayShort(millis).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(Fmt.day(millis), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            Text(Fmt.monthShort(millis), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier, shape = CircleShape, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
fun VisitStatusPill(visit: Visit, modifier: Modifier = Modifier) {
    val sc = statusColors
    val now = System.currentTimeMillis()
    val (text, bg, fg) = when (visit.status) {
        VisitStatus.PLANNED -> if (visit.end < now) Triple("Status offen", sc.warnContainer, sc.onWarnContainer)
        else Triple("Geplant", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        VisitStatus.DONE -> Triple("Stattgefunden", sc.goodContainer, sc.onGoodContainer)
        VisitStatus.SHORTENED -> Triple("Verkürzt", sc.warnContainer, sc.onWarnContainer)
        VisitStatus.NO_SHOW -> Triple("Nicht erschienen", sc.badContainer, sc.onBadContainer)
        VisitStatus.CANCELLED -> when (visit.cancellation?.by) {
            CancelledBy.FATHER -> Triple("Abgesagt vom Vater", sc.badContainer, sc.onBadContainer)
            CancelledBy.CHILD_ILL -> Triple("Abgesagt · krank", sc.neutralContainer, sc.onNeutralContainer)
            CancelledBy.MOTHER -> Triple("Abgesagt von mir", sc.neutralContainer, sc.onNeutralContainer)
            else -> Triple("Abgesagt", sc.neutralContainer, sc.onNeutralContainer)
        }
    }
    Pill(text, bg, fg, modifier)
}

/** Stimmungsauswahl mit 5 Gesichtern – die Auswahl „morpht“ in eine Expressive-Form. */
@Composable
fun MoodSelector(value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Moods.emoji.forEachIndexed { i, e ->
                val v = i + 1
                val selected = value == v
                val scale by animateFloatAsState(if (selected) 1.15f else 1f, label = "mood")
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "moodBg",
                )
                val shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape
                Box(
                    Modifier
                        .scale(scale)
                        .size(54.dp)
                        .clip(shape)
                        .background(bg)
                        .clickable { onChange(if (selected) null else v) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 26.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            Moods.labelOf(value),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun TagPicker(selected: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    var custom by remember { mutableStateOf("") }
    val sc = statusColors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val known = Tags.groups.flatMap { it.second }.toSet()
        val extra = selected.filterNot { it in known }
        (Tags.groups + if (extra.isNotEmpty()) listOf("Eigene" to extra) else emptyList()).forEach { (group, tags) ->
            Column {
                Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    tags.forEach { t ->
                        val on = t in selected
                        val concerning = t in Tags.concerning
                        FilterChip(
                            selected = on,
                            onClick = { onChange(if (on) selected - t else selected + t) },
                            label = { Text(t) },
                            leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                            colors = if (concerning) FilterChipDefaults.filterChipColors(
                                selectedContainerColor = sc.warnContainer, selectedLabelColor = sc.onWarnContainer,
                                selectedLeadingIconColor = sc.onWarnContainer,
                            ) else FilterChipDefaults.filterChipColors(),
                            shape = CircleShape,
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("Eigene Beobachtung hinzufügen") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                AnimatedVisibility(custom.isNotBlank()) {
                    TextButton(onClick = { onChange(selected + custom.trim()); custom = "" }) { Text("Hinzufügen") }
                }
            },
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    supporting: String? = null,
    leading: ImageVector? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        supportingText = supporting?.let { { Text(it) } },
        leadingIcon = leading?.let { { Icon(it, null) } },
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Klickbares Feld, das wie ein Textfeld aussieht. */
@Composable
fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
        }
    }
}

@Composable
fun DatePickerField(label: String, date: LocalDate?, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Datum wählen") {
    var open by remember { mutableStateOf(false) }
    PickerField(label, date?.let { Fmt.date(it) } ?: placeholder, Icons.Outlined.CalendarMonth, { open = true }, modifier)
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Fmt.dateToPicker(date ?: LocalDate.now()))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { onChange(Fmt.pickerToDate(it)) }; open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
fun TimePickerField(label: String, time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerField(label, "${Fmt.time(time)} Uhr", Icons.Outlined.Schedule, { open = true }, modifier)
    if (open) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
            confirmButton = { TextButton(onClick = { onChange(LocalTime.of(state.hour, state.minute)); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ShapeBadge(shape = MaterialShapes.Sunny.toShape(), size = 96.dp, color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(emoji, fontSize = 40.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    trailing: (@Composable () -> Unit)? = { Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Surface(shape = MaterialTheme.shapes.small, color = iconColor, contentColor = iconContent, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}

@Composable
fun LabeledText(label: String, text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Horizontale Auswahl-Chips für Enums u. Ä. */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, wrap: Boolean = true) {
    val chips: @Composable () -> Unit = {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(label(o)) },
                leadingIcon = if (o == selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                shape = CircleShape,
            )
        }
    }
    if (wrap) FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    else Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
}

@Composable
fun OutlinedInfo(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
