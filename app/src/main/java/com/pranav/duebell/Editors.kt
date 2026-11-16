package com.pranav.duebell

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderEditor(initial: Reminder?, onSave: (String, Long, Boolean, Int?) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var dueAt by rememberSaveable(initial?.id) {
        mutableLongStateOf(initial?.dueAt ?: (System.currentTimeMillis() / MINUTE + 5) * MINUTE)
    }
    var onUnlock by rememberSaveable(initial?.id) { mutableStateOf(initial?.onUnlock ?: false) }
    var failsafe by rememberSaveable(initial?.id) { mutableStateOf(initial?.failsafeMinutes != null) }
    var minutes by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.failsafeMinutes ?: 60) }
    var picker by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val due = Instant.ofEpochMilli(dueAt).atZone(zone)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120); error = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What should you remember?") },
            singleLine = true,
        )
        SectionTitle("Due")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = "date" }) {
                Text(due.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
            }
            OutlinedButton(onClick = { picker = "time" }) {
                Text(timeLabel(context, due.hour, due.minute))
            }
        }
        SectionTitle("Follow-ups")
        OptionCard(
            "Remind on unlock",
            "After you tap Remind again, DueBell tries the next unlock and schedules a backup notification after 15 minutes.",
            onUnlock,
            onChecked = { onUnlock = it },
        )
        OptionCard(
            "Call-style failsafe",
            "Ring if the reminder is still pending. You can snooze the ring for one hour.",
            failsafe,
            onChecked = { failsafe = it },
        )
        if (failsafe) {
            Text("Ring after the due time", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120).forEach { delay ->
                    FilterChip(
                        selected = minutes == delay,
                        onClick = { minutes = delay },
                        label = { Text(if (delay < 60) "$delay min" else if (delay == 60) "1 hour" else "2 hours") },
                    )
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                error = when {
                    title.isBlank() -> "Enter a reminder title."
                    dueAt <= System.currentTimeMillis() -> "Choose a date and time in the future."
                    else -> null
                }
                if (error == null) onSave(title.trim(), dueAt, onUnlock, minutes.takeIf { failsafe })
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save reminder")
        }
    }

    when (picker) {
        "date" -> PickDate(due.toLocalDate(), { picker = "" }) { date ->
            dueAt = date.atTime(due.hour, due.minute).atZone(zone).toInstant().toEpochMilli()
            picker = ""
            error = null
        }
        "time" -> PickTime(due.hour, due.minute, { picker = "" }) { hour, minute ->
            dueAt = due.toLocalDate().atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
            picker = ""
            error = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlarmEditor(initial: WakeAlarm?, onSave: (String, Int, Int, Repeat, Boolean) -> Unit) {
    val context = LocalContext.current
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var hour by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.hour ?: 7) }
    var minute by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.minute ?: 0) }
    var repeat by rememberSaveable(initial?.id) { mutableStateOf(initial?.repeat ?: Repeat.ONCE) }
    var walk by rememberSaveable(initial?.id) { mutableStateOf(initial?.walk ?: false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val stepSensor = context.stepDetector() != null
    val motion = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        walk = granted
        denied = !granted
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(120) },
            label = { Text("Alarm label (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SectionTitle("Ring at")
        OutlinedButton(onClick = { pickingTime = true }) { Text(timeLabel(context, hour, minute)) }
        SectionTitle("Repeat")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Repeat.entries.forEach { choice ->
                FilterChip(selected = repeat == choice, onClick = { repeat = choice }, label = { Text(choice.label) })
            }
        }
        SectionTitle("Dismissal challenge")
        Text("Solve a short addition problem when the alarm rings.", style = MaterialTheme.typography.bodyMedium)
        OptionCard(
            "Also walk 10 steps",
            if (stepSensor) "Carry your phone and walk after solving the problem." else "This device has no step detector.",
            checked = walk && stepSensor,
            enabled = stepSensor,
        ) { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= 29 && !context.hasMotionAccess()) {
                motion.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                walk = enabled
                denied = false
            }
        }
        if (denied) Text("Physical activity permission is needed for the walking challenge.")
        Button(
            onClick = { onSave(title.trim().ifBlank { "Wake-up alarm" }, hour, minute, repeat, walk && stepSensor) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save alarm") }
    }

    if (pickingTime) PickTime(hour, minute, { pickingTime = false }) { newHour, newMinute ->
        hour = newHour
        minute = newMinute
        pickingTime = false
    }
}

@Composable
private fun OptionCard(title: String, description: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = checked, onCheckedChange = onChecked, enabled = enabled,
                modifier = Modifier.padding(end = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDate(date: LocalDate, close: () -> Unit, select: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = close,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { select(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text("Select") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickTime(hour: Int, minute: Int, close: () -> Unit, select: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(hour, minute, android.text.format.DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Select time") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { select(state.hour, state.minute) }) { Text("Select") } },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}
