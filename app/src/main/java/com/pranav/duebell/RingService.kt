package com.pranav.duebell

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var activeId: String? = null
    private var playback: MediaPlayer? = null
    private var backupTone: ToneGenerator? = null
    private var observing = false

    private val beep = object : Runnable {
        override fun run() {
            val generator = backupTone ?: return
            generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 800)
            handler.postDelayed(this, 1400)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = bell.state.value
        val ring = state.ringing
        if (ring == null) {
            // A notification action may finish the session before the service starts.
            if (!running) {
                ServiceCompat.startForeground(
                    this, BellAlerts.FOREGROUND_RING_ID, bell.alerts.endedRing(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            }
            quiet()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        running = true
        present(ring, state.ringtone)
        if (!observing) {
            observing = true
            scope.launch {
                bell.state.collect {
                    val current = it.ringing
                    if (current == null) {
                        quiet()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        running = false
                    } else {
                        present(current, it.ringtone)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun present(ring: RingSession, selectedSound: String?) {
        if (activeId == ring.id) return
        quiet()
        activeId = ring.id
        ServiceCompat.startForeground(
            this,
            BellAlerts.FOREGROUND_RING_ID,
            bell.alerts.ring(ring),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        val defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val sound = if (ring.kind == RingKind.REMINDER) {
            selectedSound?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } else {
            defaultAlarm
        }
        if (!play(sound) && (sound == defaultAlarm || !play(defaultAlarm))) {
            backupTone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull()
            handler.post(beep)
        }
    }

    private fun play(uri: Uri): Boolean {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(this, uri)
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            player.isLooping = true
            player.prepare()
            player.start()
            playback = player
            true
        } catch (_: Exception) {
            player.release()
            false
        }
    }

    private fun quiet() {
        handler.removeCallbacks(beep)
        backupTone?.release()
        backupTone = null
        playback?.release()
        playback = null
        activeId = null
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        quiet()
        super.onDestroy()
    }

    internal companion object {
        var running = false
            private set
    }
}
