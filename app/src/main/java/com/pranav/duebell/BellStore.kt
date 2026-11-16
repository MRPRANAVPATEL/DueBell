package com.pranav.duebell

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

internal class BellStore(context: Context) {
    private val preferences = context.getSharedPreferences("duebell", Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(
        preferences.getString("state", null)?.let { decode(JSONObject(it)) } ?: BellState(),
    )
    val state = changes.asStateFlow()

    fun save(state: BellState) {
        if (state == changes.value) return
        preferences.edit().putString("state", encode(state).toString()).apply()
        changes.value = state
    }

    private fun encode(state: BellState): JSONObject = JSONObject()
        .put("reminders", JSONArray(state.reminders.map { reminder ->
            JSONObject()
                .put("id", reminder.id)
                .put("title", reminder.title)
                .put("dueAt", reminder.dueAt)
                .put("onUnlock", reminder.onUnlock)
                .put("failsafe", reminder.failsafeMinutes)
                .put("pending", reminder.pending)
                .put("doneAt", reminder.doneAt)
                .put("nudgeAt", reminder.nudgeAt)
                .put("ringAt", reminder.ringAt)
        }))
        .put("alarms", JSONArray(state.alarms.map { alarm ->
            JSONObject()
                .put("id", alarm.id)
                .put("title", alarm.title)
                .put("hour", alarm.hour)
                .put("minute", alarm.minute)
                .put("repeat", alarm.repeat.name)
                .put("walk", alarm.walk)
                .put("nextAt", alarm.nextAt)
        }))
        .put("rings", JSONArray(state.rings.map { ring ->
            JSONObject()
                .put("itemId", ring.itemId)
                .put("title", ring.title)
                .put("kind", ring.kind.name)
                .put("dueAt", ring.dueAt)
                .put("walk", ring.walk)
                .put("mathSolved", ring.mathSolved)
                .put("steps", ring.steps)
        }))
        .put("ringtone", state.ringtone)

    private fun decode(objectValue: JSONObject): BellState = BellState(
        reminders = objectValue.items("reminders") {
            Reminder(
                id = getString("id"),
                title = getString("title"),
                dueAt = getLong("dueAt"),
                onUnlock = getBoolean("onUnlock"),
                failsafeMinutes = nullableLong("failsafe")?.toInt(),
                pending = getBoolean("pending"),
                doneAt = nullableLong("doneAt"),
                nudgeAt = nullableLong("nudgeAt"),
                ringAt = nullableLong("ringAt"),
            )
        },
        alarms = objectValue.items("alarms") {
            WakeAlarm(
                id = getString("id"),
                title = getString("title"),
                hour = getInt("hour"),
                minute = getInt("minute"),
                repeat = Repeat.valueOf(getString("repeat")),
                walk = getBoolean("walk"),
                nextAt = nullableLong("nextAt"),
            )
        },
        rings = objectValue.items("rings") {
            RingSession(
                itemId = getString("itemId"),
                title = getString("title"),
                kind = RingKind.valueOf(getString("kind")),
                dueAt = getLong("dueAt"),
                walk = getBoolean("walk"),
                mathSolved = getBoolean("mathSolved"),
                steps = getInt("steps"),
            )
        },
        ringtone = objectValue.optString("ringtone").takeIf { it.isNotBlank() },
    )

    private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun <T> JSONObject.items(key: String, parse: JSONObject.() -> T): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it).parse() }
    }
}
