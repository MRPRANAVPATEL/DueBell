package com.pranav.duebell

import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class RingActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DueBellTheme(ringing = true) {
                val state by bell.state.collectAsState()
                val ring = state.ringing
                LaunchedEffect(ring == null) {
                    if (ring == null) finishAndRemoveTask()
                }
                if (ring != null) RingScreen(ring, bell)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bell.onVisible()
    }
}

@Composable
private fun RingScreen(ring: RingSession, bell: BellController) {
    val context = LocalContext.current
    DisposableEffect(ring.id, ring.walk, ring.mathSolved) {
        val manager = context.getSystemService(SensorManager::class.java)
        val detector = context.stepDetector()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.firstOrNull() == 1f) bell.countStep(ring.id)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        if (ring.walk) {
            if (detector == null || !context.hasMotionAccess() || manager == null) {
                bell.useMathOnly(ring.id)
            } else if (ring.mathSolved) {
                val registered = runCatching { manager.registerListener(listener, detector, SensorManager.SENSOR_DELAY_NORMAL) }
                if (registered.getOrDefault(false).not()) bell.useMathOnly(ring.id)
            }
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Notifications, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp),
                )
                Text(
                    if (ring.kind == RingKind.ALARM) "Wake-up alarm" else "Failsafe reminder",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(ring.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                when {
                    ring.kind == RingKind.REMINDER -> {
                        Button(onClick = { bell.done(ring.itemId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Done")
                        }
                        OutlinedButton(onClick = { bell.snooze(ring.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Snooze 1 hour")
                        }
                    }
                    ring.mathSolved -> {
                        Text("Walk 10 steps with your phone", textAlign = TextAlign.Center)
                        CircularProgressIndicator(
                            progress = { ring.steps.toFloat() / STEP_GOAL },
                            modifier = Modifier.size(76.dp),
                        )
                        Text("${ring.steps} / $STEP_GOAL steps", style = MaterialTheme.typography.titleLarge)
                    }
                    else -> MathPad(ring) { answer -> bell.solveMath(ring.id, answer) }
                }
            }
        }
    }
}

@Composable
private fun MathPad(ring: RingSession, onAnswer: (Int?) -> Boolean) {
    var answer by rememberSaveable(ring.id) { mutableStateOf("") }
    var wrong by rememberSaveable(ring.id) { mutableStateOf(false) }
    Text(
        if (ring.walk) "Solve the problem, then walk 10 steps." else "Solve the problem to stop the alarm.",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "${ring.firstNumber} + ${ring.secondNumber} = " + answer.ifEmpty { "?" },
        style = MaterialTheme.typography.headlineMedium,
    )
    if (wrong) Text("Try again", color = MaterialTheme.colorScheme.error)

    listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Clear", "0", "Check"),
    ).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { key ->
                val modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                if (key == "Check") {
                    Button(
                        onClick = {
                            wrong = !onAnswer(answer.toIntOrNull())
                            if (wrong) answer = ""
                        },
                        enabled = answer.isNotEmpty(),
                        modifier = modifier,
                    ) { Text(key) }
                } else {
                    OutlinedButton(
                        onClick = {
                            answer = if (key == "Clear") "" else (answer + key).take(2)
                            wrong = false
                        },
                        modifier = modifier,
                    ) { Text(key) }
                }
            }
        }
    }
}
