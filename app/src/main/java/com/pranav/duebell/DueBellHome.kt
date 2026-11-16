package com.pranav.duebell

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueBellHome(bell: BellController, access: AlertAccess, refreshAccess: () -> Unit) {
    val state by bell.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("home") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val goHome = { page = "home"; editingId = null }
    val message: (String) -> Unit = { text -> scope.launch { snackbar.showSnackbar(text) } }
    val editReminder: (String?) -> Unit = { editingId = it; page = "reminder" }
    val editAlarm: (String?) -> Unit = { editingId = it; page = "alarm" }
    val openRinging = {
        context.startActivity(
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }
    BackHandler(page != "home", goHome)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            "settings" -> "Alert setup"
                            "reminder" -> if (editingId == null) "New reminder" else "Edit reminder"
                            "alarm" -> if (editingId == null) "New alarm" else "Edit alarm"
                            else -> "DueBell"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (page != "home") {
                        IconButton(onClick = goHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (page == "home") {
                        IconButton(onClick = { page = "settings" }) { Icon(Icons.Default.Settings, "Alert setup") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (page == "home") {
                val label = if (tab == 0) "New reminder" else "New alarm"
                ExtendedFloatingActionButton(
                    onClick = { if (tab == 0) editReminder(null) else editAlarm(null) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(label) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                "settings" -> AlertSetup(bell, state, access, refreshAccess, message)
                "reminder" -> ReminderEditor(state.reminders.find { it.id == editingId }) { title, at, unlock, delay ->
                    bell.addReminder(editingId, title, at, unlock, delay)
                    goHome()
                    message("Reminder scheduled for " + dueLabel(context, at))
                }
                "alarm" -> AlarmEditor(state.alarms.find { it.id == editingId }) { title, hour, minute, repeat, walk ->
                    bell.addAlarm(editingId, title, hour, minute, repeat, walk)
                    goHome()
                    message("Alarm saved")
                }
                else -> Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Reminders") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Alarms") })
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.ringing?.let { ring ->
                            item(key = "live") {
                                Card(
                                    onClick = openRinging,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text("Ringing", style = MaterialTheme.typography.labelLarge)
                                        Text(ring.title, style = MaterialTheme.typography.titleMedium)
                                        Text("Tap to open", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        if (!access.notifications || !access.precise || !access.fullScreen) {
                            item(key = "setup") {
                                OutlinedCard(onClick = { page = "settings" }) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null)
                                        Column {
                                            Text("Finish alert setup", style = MaterialTheme.typography.titleMedium)
                                            Text("Check permissions for your reminders and alarms.")
                                        }
                                    }
                                }
                            }
                        }
                        if (tab == 0) {
                            val pending = state.reminders.filter { it.pending && it.doneAt == null }.sortedBy { it.dueAt }
                            val scheduled = state.reminders.filter { !it.pending && it.doneAt == null }.sortedBy { it.dueAt }
                            val done = state.reminders.filter { it.doneAt != null }.sortedByDescending { it.doneAt }
                            if (state.reminders.isEmpty()) {
                                item(key = "no-reminders") {
                                    EmptyCard("No reminders yet", "Add a task and choose how DueBell should follow up.")
                                }
                            }
                            listOf("Pending" to pending, "Upcoming" to scheduled, "Completed" to done).forEach { (title, group) ->
                                if (group.isNotEmpty()) {
                                    item(key = title) { SectionTitle(title) }
                                    items(group, key = { it.id }) { reminder ->
                                        ReminderCard(
                                            reminder,
                                            onDone = { bell.done(reminder.id) },
                                            onAgain = {
                                                bell.again(reminder.id)
                                                message(if (reminder.onUnlock) "Follow-up queued" else "Reminder kept pending")
                                            },
                                            onEdit = { editReminder(reminder.id) },
                                            onDelete = { deleteId = reminder.id },
                                        )
                                    }
                                }
                            }
                        } else {
                            if (state.alarms.isEmpty()) {
                                item(key = "no-alarms") {
                                    EmptyCard("No alarms yet", "Set a wake-up time and choose a challenge.")
                                }
                            }
                            items(state.alarms.sortedWith(compareBy<WakeAlarm> { it.hour }.thenBy { it.minute }), key = { it.id }) { alarm ->
                                val active = state.rings.any { it.itemId == alarm.id }
                                AlarmCard(
                                    alarm = alarm,
                                    enabled = alarm.nextAt != null || active,
                                    active = active,
                                    onToggle = { bell.toggleAlarm(alarm.id, it) },
                                    onEdit = { editAlarm(alarm.id) },
                                    onDelete = { deleteId = alarm.id },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete this item?") },
            text = { Text("Its scheduled alerts will be removed.") },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = { bell.delete(id); deleteId = null }) { Text("Delete") }
            },
        )
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title, Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyCard(title: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder, onDone: () -> Unit, onAgain: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(reminder.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        reminder.doneAt?.let { "Finished " + dueLabel(context, it) } ?: dueLabel(context, reminder.dueAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (reminder.doneAt == null) {
                        val details = listOfNotNull(
                            if (reminder.onUnlock) "On unlock" else null,
                            reminder.ringAt?.let { "Rings " + dueLabel(context, it) },
                        )
                        if (details.isNotEmpty()) {
                            Text(details.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                ItemMenu(onEdit = onEdit.takeIf { reminder.doneAt == null }, onDelete = onDelete)
            }
            if (reminder.pending) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDone) { Text("Done") }
                    TextButton(onClick = onAgain) { Text("Remind again") }
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(alarm: WakeAlarm, enabled: Boolean, active: Boolean, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        timeLabel(context, alarm.hour, alarm.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(alarm.title, style = MaterialTheme.typography.titleMedium)
                }
                Switch(
                    checked = enabled, onCheckedChange = onToggle, enabled = !active,
                    modifier = Modifier.semantics { contentDescription = "Enable alarm " + alarm.title },
                )
                if (!active) ItemMenu(onEdit, onDelete)
            }
            Text(
                alarm.repeat.label + if (alarm.walk) " · Math + 10 steps" else " · Math",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (alarm.nextAt != null) {
                Text("Next " + dueLabel(context, alarm.nextAt), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ItemMenu(onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "Item options") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onEdit != null) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { expanded = false; onEdit() })
            }
            DropdownMenuItem(text = { Text("Delete") }, onClick = { expanded = false; onDelete() })
        }
    }
}

internal fun timeLabel(context: Context, hour: Int, minute: Int): String {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(pattern))
}

internal fun dueLabel(context: Context, millis: Long): String {
    val value = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val date = value.toLocalDate()
    val today = LocalDate.now()
    val label = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    return label + " at " + timeLabel(context, value.hour, value.minute)
}
