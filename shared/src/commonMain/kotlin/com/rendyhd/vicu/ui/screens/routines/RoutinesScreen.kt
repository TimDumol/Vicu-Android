package com.rendyhd.vicu.ui.screens.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rendyhd.vicu.domain.model.OccurrenceStatus
import com.rendyhd.vicu.domain.model.Routine
import com.rendyhd.vicu.domain.model.RoutineDay
import com.rendyhd.vicu.domain.model.RoutineDraft
import com.rendyhd.vicu.domain.model.RoutineOccurrence
import com.rendyhd.vicu.domain.model.RoutineOccurrenceRecord
import com.rendyhd.vicu.domain.model.RoutinePeriod
import com.rendyhd.vicu.domain.model.RoutineSchedule
import com.rendyhd.vicu.domain.model.RoutineSlot
import com.rendyhd.vicu.ui.components.shared.EmptyState
import com.rendyhd.vicu.ui.components.shared.IconColorPicker
import com.rendyhd.vicu.ui.components.shared.LocalFabAlignStart
import com.rendyhd.vicu.ui.components.shared.copyPlainText
import com.rendyhd.vicu.ui.components.shared.LocalToday
import com.rendyhd.vicu.ui.components.shared.ScreenFab
import com.rendyhd.vicu.ui.components.shared.VicuTopAppBar
import org.koin.compose.viewmodel.koinViewModel
import com.rendyhd.vicu.ui.components.shared.LocalDateFormat
import com.rendyhd.vicu.util.DateContext
import com.rendyhd.vicu.util.DateDisplay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.rendyhd.vicu.domain.model.RoutineDefinition
import com.rendyhd.vicu.ui.components.shared.LocalIs24Hour
import com.rendyhd.vicu.ui.components.shared.SmartListIdentity
import com.rendyhd.vicu.ui.theme.LocalVicuColors
import com.rendyhd.vicu.ui.theme.routineMarkColor
import com.rendyhd.vicu.util.DateDisplayFormat
import com.rendyhd.vicu.util.RoutineDefinitionRules
import kotlinx.datetime.LocalTime

@Composable
fun RoutinesScreen(
    onOpenDrawer: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: RoutinesViewModel = koinViewModel(),
) {
    val routinesEnabled by viewModel.routinesEnabled.collectAsStateWithLifecycle()
    if (!routinesEnabled) {
        RoutinesTurnedOff(onOpenDrawer, onNavigateToSearch)
        return
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val remindersEnabled by viewModel.remindersEnabled.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var pageMenuOpen by remember { mutableStateOf(false) }
    var editorRoutine by remember { mutableStateOf<Routine?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Routine?>(null) }
    var historyRoutine by remember { mutableStateOf<Routine?>(null) }

    Scaffold(
        topBar = {
            VicuTopAppBar(
                title = { Text("Routines") },
                onOpenDrawer = onOpenDrawer,
                onNavigateToSearch = onNavigateToSearch,
                extraActions = {
                    Box {
                        IconButton(onClick = { pageMenuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, "Routine options")
                        }
                        DropdownMenu(expanded = pageMenuOpen, onDismissRequest = { pageMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (remindersEnabled) "Pause all reminders" else "Resume reminders") },
                                trailingIcon = {
                                    Switch(
                                        checked = remindersEnabled,
                                        onCheckedChange = null,
                                    )
                                },
                                onClick = {
                                    pageMenuOpen = false
                                    viewModel.setRemindersEnabled(!remindersEnabled)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy history as CSV") },
                                onClick = {
                                    pageMenuOpen = false
                                    viewModel.exportCsv { export ->
                                        scope.launch {
                                            clipboard.copyPlainText("Routine history", export.csv)
                                            snackbar.showSnackbar(
                                                if (export.complete) {
                                                    "Routine history copied"
                                                } else {
                                                    "Routine history copied, without older history that could not be loaded"
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ScreenFab(
                onClick = {
                    editorRoutine = null
                    showEditor = true
                },
                label = "New routine",
                // It adds a routine, not a task: holding it does not listen.
                taskDefaults = null,
            )
        },
        floatingActionButtonPosition = if (LocalFabAlignStart.current) FabPosition.Start else FabPosition.End,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary", contentType = "summary") {
                RoutineDaySummary(
                    day = state.day,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (state.day.occurrences.isEmpty()) {
                item(key = "empty_today", contentType = "empty") {
                    Text(
                        text = if (state.active.isEmpty()) {
                            "Add something you do regularly, like taking vitamins or watering the plants."
                        } else {
                            "Nothing scheduled for today."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            } else {
                items(state.day.occurrences, key = { it.key }, contentType = { "occurrence" }) { occurrence ->
                    RoutineOccurrenceRow(
                        occurrence = occurrence,
                        onToggle = { viewModel.toggle(occurrence) },
                        onSkip = { viewModel.skip(occurrence) },
                        modifier = Modifier.padding(horizontal = 12.dp).animateItem(),
                    )
                }
            }

            item(key = "all_header", contentType = "header") { SectionLabel("ALL ROUTINES") }
            items(state.active, key = { it.definition.id }, contentType = { "routine" }) { routine ->
                RoutineDefinitionCard(
                    routine = routine,
                    onEdit = {
                        editorRoutine = routine
                        showEditor = true
                    },
                    onArchive = { viewModel.archive(routine, true) },
                    onDelete = { pendingDelete = routine },
                    onOpenHistory = { historyRoutine = routine },
                    modifier = Modifier.padding(horizontal = 12.dp).animateItem(),
                )
            }

            if (state.archived.isNotEmpty()) {
                item(key = "archived_header", contentType = "header") { SectionLabel("ARCHIVED") }
                items(state.archived, key = { "archived_${it.definition.id}" }, contentType = { "routine" }) { routine ->
                    RoutineDefinitionCard(
                        routine = routine,
                        onEdit = {},
                        onArchive = { viewModel.archive(routine, false) },
                        onDelete = { pendingDelete = routine },
                        onOpenHistory = { historyRoutine = routine },
                        archived = true,
                        modifier = Modifier.padding(horizontal = 12.dp).animateItem(),
                    )
                }
            }

            val (newer, damaged) = state.issues.partition { it.updateRequired }
            if (newer.isNotEmpty()) {
                item(key = "issues-newer", contentType = "notice") {
                    Text(
                        text = "${newer.size} ${if (newer.size == 1) "routine was" else "routines were"} saved by a newer version of Vicu. Update Vicu to see ${if (newer.size == 1) "it" else "them"}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (damaged.isNotEmpty()) {
                item(key = "issues", contentType = "notice") {
                    Text(
                        text = "${damaged.size} routine ${if (damaged.size == 1) "record needs" else "records need"} repair",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            state.archiveWarning?.let { warning ->
                item(key = "archive-warning", contentType = "notice") {
                    Text(
                        text = warning,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (showEditor) {
        RoutineEditorDialog(
            routine = editorRoutine,
            saving = state.isSaving,
            onDismiss = { if (!state.isSaving) showEditor = false },
            onSave = { draft ->
                viewModel.save(draft, editorRoutine?.definition?.id) {
                    showEditor = false
                }
            },
        )
    }

    pendingDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${routine.definition.name}?") },
            text = { Text("Its synced and local history will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePermanently(routine)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    historyRoutine?.let { routine ->
        // One flow per routine, not one per recomposition: a new instance would restart the
        // collection and flash the dialog empty.
        val historyFlow = remember(routine.definition.id) { viewModel.observeHistory(routine.definition.id) }
        val history by historyFlow.collectAsStateWithLifecycle(emptyList())
        RoutineHistoryDialog(
            routine = routine,
            history = history,
            onDismiss = { historyRoutine = null },
        )
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }
}

@Composable
fun RoutineDaySummary(day: RoutineDay, modifier: Modifier = Modifier) {
    val progress = if (day.scheduledCount == 0) "Ready when you are" else "${day.completedCount} of ${day.scheduledCount} done"
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            CircularProgressIndicator(
                progress = { if (day.scheduledCount == 0) 0f else day.completedCount.toFloat() / day.scheduledCount },
                modifier = Modifier.fillMaxSize(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text(day.completedCount.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Today’s rhythm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RoutineOccurrenceRow(
    occurrence: RoutineOccurrence,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val complete = occurrence.status == OccurrenceStatus.COMPLETED
    val skipped = occurrence.status == OccurrenceStatus.SKIPPED
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (complete) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (complete) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (complete) "Undo" else "Complete",
                    tint = if (complete) MaterialTheme.colorScheme.primary else routineTint(occurrence.routine.definition),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    occurrence.routine.definition.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = buildList {
                    add(timeText(occurrence.slot.reminderMinutes, LocalDateFormat.current))
                    if (occurrence.status == OccurrenceStatus.NOT_LOGGED) add("Not logged")
                    if (skipped) add("Skipped")
                }.joinToString(" · ")
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!complete && !skipped) {
                TextButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun RoutineDefinitionCard(
    routine: Routine,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onOpenHistory: () -> Unit,
    archived: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpenHistory),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                SmartListIdentity.ROUTINES.icon,
                contentDescription = null,
                tint = routineTint(routine.definition),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.definition.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    routineSummary(routine.definition, LocalDateFormat.current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, "Routine actions") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (!archived) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (archived) "Restore" else "Archive") },
                        leadingIcon = { Icon(if (archived) Icons.Outlined.Restore else Icons.Outlined.Archive, null) },
                        onClick = { menuOpen = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** The colour of a routine's ring and icon on its card: its own colour, or the Routines colour without one. */
@Composable
private fun routineTint(definition: RoutineDefinition): Color {
    val scheme = MaterialTheme.colorScheme
    return routineMarkColor(definition.color, scheme.onSurface, listOf(scheme.surfaceContainerLow))
        ?: SmartListIdentity.ROUTINES.color(LocalVicuColors.current.identity)
}

@Composable
private fun RoutineHistoryDialog(
    routine: Routine,
    history: List<RoutineOccurrenceRecord>,
    onDismiss: () -> Unit,
) {
    val logged = history.filter { it.status != OccurrenceStatus.PENDING }
    val completed = logged.count { it.status == OccurrenceStatus.COMPLETED }
    val adherence = if (logged.isEmpty()) 0 else (completed * 100 / logged.size)
    val historyToday = LocalToday.current
    val dateFormat = LocalDateFormat.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(routine.definition.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (logged.isEmpty()) "No history yet" else "$adherence% completed · $completed of ${logged.size} logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (logged.isEmpty()) {
                    Text("Daily activity will appear here as you complete or skip it.")
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(logged.take(30), key = { it.key }, contentType = { "record" }) { record ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (record.status == OccurrenceStatus.COMPLETED) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (record.status == OccurrenceStatus.COMPLETED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        runCatching { DateDisplay.formatDay(DateContext.CHIP, LocalDate.parse(record.scheduledDate), historyToday, dateFormat) }
                                            .getOrDefault(record.scheduledDate),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        record.status.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(timeText(record.scheduledMinutes, dateFormat), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
            }
        }
    }
}

/** One time of a routine in the editor; [slot] is the stored slot it edits (null for a new time). */
private data class EditorTime(
    val key: Int,
    val minutes: Int,
    val remind: Boolean,
    val followUpMinutes: Int,
    val slot: RoutineSlot?,
)

private val FollowUpOptions = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hour")

private fun followUpText(minutes: Int): String =
    FollowUpOptions.firstOrNull { it.first == minutes }?.second ?: "$minutes min"

/**
 * The routine editor: a name, one to five times (each with its reminder and follow-up), how it
 * repeats and its colour. Nothing else is chosen; the repository writes what older app versions
 * still read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditorDialog(
    routine: Routine?,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (RoutineDraft) -> Unit,
) {
    val definition = routine?.definition
    val dateFormat = LocalDateFormat.current
    var name by remember(routine) { mutableStateOf(definition?.name.orEmpty()) }
    // Empty is no colour of its own; a colour picked on the desktop is kept as it is.
    var color by remember(routine) { mutableStateOf(definition?.color.orEmpty()) }
    var times by remember(routine) {
        mutableStateOf(
            definition?.slots.orEmpty().sortedBy { it.reminderMinutes }.mapIndexed { index, slot ->
                EditorTime(index, slot.reminderMinutes, slot.reminderEnabled, slot.followUpMinutes, slot)
            }.ifEmpty { listOf(EditorTime(0, NEW_ROUTINE_MINUTES, remind = true, followUpMinutes = 0, slot = null)) },
        )
    }
    var nextKey by remember(routine) { mutableStateOf(times.size) }
    var pickingTime by remember(routine) { mutableStateOf<Int?>(null) }
    var afterCompletion by remember(routine) { mutableStateOf(definition?.schedule is RoutineSchedule.AfterCompletion) }
    var interval by remember(routine) {
        mutableStateOf((definition?.schedule as? RoutineSchedule.AfterCompletion)?.intervalDays?.toString() ?: "14")
    }
    val today = LocalToday.current.toString()
    val existingCalendar = definition?.schedule as? RoutineSchedule.Calendar
    var weekInterval by remember(routine) { mutableStateOf(existingCalendar?.weekInterval?.coerceAtLeast(1) ?: 1) }
    // Empty means every day, as stored.
    var weekdays by remember(routine) {
        mutableStateOf(existingCalendar?.weekdays?.takeIf { it.size < 7 } ?: emptySet())
    }
    val intervalDays = interval.toIntOrNull()
    val valid = name.isNotBlank() && times.isNotEmpty() &&
        (!afterCompletion || (intervalDays != null && intervalDays in 1..365))

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(if (routine == null) "New routine" else "Edit routine", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Take vitamins") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(18.dp))
                    EditorLabel("TIMES")
                    times.forEach { time ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { pickingTime = time.key }) {
                                Text(timeText(time.minutes, dateFormat), style = MaterialTheme.typography.titleMedium)
                            }
                            IconButton(onClick = {
                                times = times.map { if (it.key == time.key) it.copy(remind = !it.remind) else it }
                            }) {
                                Icon(
                                    if (time.remind) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                                    contentDescription = if (time.remind) "Reminder on" else "Reminder off",
                                    tint = if (time.remind) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                var followUpMenu by remember { mutableStateOf(false) }
                                TextButton(onClick = { followUpMenu = true }, enabled = time.remind) {
                                    Text(
                                        "Follow up: ${followUpText(time.followUpMinutes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                DropdownMenu(expanded = followUpMenu, onDismissRequest = { followUpMenu = false }) {
                                    FollowUpOptions.forEach { (minutes, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                followUpMenu = false
                                                times = times.map { if (it.key == time.key) it.copy(followUpMinutes = minutes) else it }
                                            },
                                        )
                                    }
                                }
                            }
                            if (times.size > 1) {
                                IconButton(onClick = { times = times.filterNot { it.key == time.key } }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Remove ${timeText(time.minutes, dateFormat)}")
                                }
                            }
                        }
                    }
                    if (times.size < RoutineDefinitionRules.MAX_TIMES) {
                        TextButton(onClick = {
                            val last = times.maxOfOrNull { it.minutes }
                            val minutes = last?.let { (it + 60).coerceAtMost(23 * 60 + 59) } ?: NEW_ROUTINE_MINUTES
                            times = times + EditorTime(nextKey, minutes, remind = true, followUpMinutes = 0, slot = null)
                            pickingTime = nextKey
                            nextKey++
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add time")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    EditorLabel("REPEAT")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !afterCompletion, onClick = { afterCompletion = false }, label = { Text("On a schedule") })
                        FilterChip(selected = afterCompletion, onClick = { afterCompletion = true }, label = { Text("After completion") })
                    }
                    Text(
                        if (afterCompletion) {
                            "Comes back a set number of days after you complete it."
                        } else {
                            "Repeats on the days you pick, whether or not you did it last time."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (afterCompletion) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Show again", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = interval,
                                onValueChange = { interval = it.filter(Char::isDigit).take(3) },
                                singleLine = true,
                                isError = intervalDays == null || intervalDays !in 1..365,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(80.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (intervalDays == 1) "day after I complete it" else "days after I complete it",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        val daily = weekdays.isEmpty()
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FilterChip(selected = daily, onClick = { weekdays = emptySet() }, label = { Text("Daily") })
                            WeekdayLetters.forEachIndexed { index, letter ->
                                val day = index + 1
                                val selected = daily || day in weekdays
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val current = if (daily) (1..7).toSet() else weekdays
                                        val next = if (selected) current - day else current + day
                                        // At least one day; all seven is every day.
                                        if (next.isNotEmpty()) weekdays = if (next.size == 7) emptySet() else next
                                    },
                                    label = { Text(letter) },
                                    modifier = Modifier.semantics { contentDescription = WeekdayNames[index] },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { weeks ->
                                FilterChip(
                                    selected = weekInterval == weeks,
                                    onClick = { weekInterval = weeks },
                                    label = { Text(if (weeks == 1) "Every week" else "Every $weeks weeks") },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    EditorLabel("COLOR")
                    IconColorPicker(selectedHex = color, onSelect = { color = it })
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = valid && !saving,
                        onClick = {
                            val slots = times.map { time ->
                                time.slot?.copy(
                                    reminderMinutes = time.minutes,
                                    reminderEnabled = time.remind,
                                    followUpMinutes = time.followUpMinutes,
                                ) ?: RoutineSlot(
                                    id = "",
                                    label = "",
                                    period = RoutinePeriod.MORNING,
                                    reminderMinutes = time.minutes,
                                    reminderEnabled = time.remind,
                                    followUpMinutes = time.followUpMinutes,
                                )
                            }
                            val schedule = if (afterCompletion) {
                                RoutineSchedule.AfterCompletion(
                                    intervalDays = intervalDays ?: 14,
                                    firstDueDate = (definition?.schedule as? RoutineSchedule.AfterCompletion)?.firstDueDate ?: today,
                                )
                            } else {
                                RoutineSchedule.Calendar(
                                    weekdays = weekdays,
                                    weekInterval = weekInterval,
                                    anchorDate = existingCalendar?.anchorDate ?: today,
                                )
                            }
                            onSave(
                                RoutineDraft(
                                    name = name,
                                    iconName = definition?.iconName.orEmpty(),
                                    color = color,
                                    schedule = schedule,
                                    slots = slots,
                                ),
                            )
                        },
                    ) { Text(if (saving) "Saving…" else "Save") }
                }
            }
        }
    }

    pickingTime?.let { key ->
        val time = times.firstOrNull { it.key == key }
        if (time == null) {
            pickingTime = null
        } else {
            val pickerState = rememberTimePickerState(
                initialHour = time.minutes / 60,
                initialMinute = time.minutes % 60,
                is24Hour = LocalIs24Hour.current,
            )
            AlertDialog(
                onDismissRequest = { pickingTime = null },
                title = { Text("Time") },
                text = { TimePicker(state = pickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        val minutes = pickerState.hour * 60 + pickerState.minute
                        times = times.map { if (it.key == key) it.copy(minutes = minutes) else it }.sortedBy { it.minutes }
                        pickingTime = null
                    }) { Text("Set") }
                },
                dismissButton = { TextButton(onClick = { pickingTime = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
    )
}

/** A new routine starts at 09:00, every day. */
private const val NEW_ROUTINE_MINUTES = 9 * 60

private val WeekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val WeekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val WeekdayShortNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun timeText(minutes: Int, format: DateDisplayFormat): String =
    DateDisplay.formatTime(LocalTime(minutes / 60, minutes % 60), format)

/** "Every day · 08:00, 20:00", "Mon, Thu, every 2 weeks · 09:00", "14 days after completion · 18:00". */
internal fun routineSummary(definition: RoutineDefinition, format: DateDisplayFormat): String {
    val schedule = when (val value = definition.schedule) {
        is RoutineSchedule.Calendar -> {
            val days = if (value.weekdays.isEmpty() || value.weekdays.size == 7) {
                "Every day"
            } else {
                value.weekdays.sorted().mapNotNull { WeekdayShortNames.getOrNull(it - 1) }.joinToString(", ")
            }
            if (value.weekInterval > 1) "$days, every ${value.weekInterval} weeks" else days
        }
        is RoutineSchedule.AfterCompletion ->
            if (value.intervalDays == 1) "1 day after completion" else "${value.intervalDays} days after completion"
    }
    val times = definition.slots.sortedBy { it.reminderMinutes }.joinToString(", ") { timeText(it.reminderMinutes, format) }
    return listOf(schedule, times).filter { it.isNotBlank() }.joinToString(" · ")
}

/** What the screen shows while routines are turned off in Settings. */
@Composable
private fun RoutinesTurnedOff(onOpenDrawer: () -> Unit, onNavigateToSearch: () -> Unit) {
    Scaffold(
        topBar = {
            VicuTopAppBar(
                title = { Text("Routines") },
                onOpenDrawer = onOpenDrawer,
                onNavigateToSearch = onNavigateToSearch,
            )
        },
    ) { padding ->
        EmptyState(
            icon = SmartListIdentity.ROUTINES.icon,
            title = "Routines are turned off",
            subtitle = "Turn them on in Settings to keep track of what you do regularly",
            modifier = Modifier.padding(padding),
        )
    }
}
