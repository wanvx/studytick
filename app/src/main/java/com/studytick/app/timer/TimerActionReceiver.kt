package com.studytick.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studytick.app.studyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_START && action != ACTION_STOP && action != ACTION_TOGGLE) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                when (action) {
                    ACTION_START -> TimerController.start(appContext)
                    ACTION_STOP -> TimerController.stop(appContext)
                    ACTION_TOGGLE -> {
                        val running = appContext.studyApp.repo.snapshotToday().running
                        if (running) TimerController.stop(appContext) else TimerController.start(appContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.studytick.app.action.START"
        const val ACTION_STOP = "com.studytick.app.action.STOP"
        const val ACTION_TOGGLE = "com.studytick.app.action.TOGGLE"
    }
}
