package com.pranav.duebell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var access by mutableStateOf(AlertAccess(false, false, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        access = bell.alerts.access()
        setContent {
            DueBellTheme {
                DueBellHome(bell, access, ::refreshAlerts)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAlerts()
    }

    private fun refreshAlerts() {
        bell.onVisible()
        access = bell.alerts.access()
    }
}
