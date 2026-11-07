package com.studytick.app.timer

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.studytick.app.data.StudyRepository
import com.studytick.app.studyApp
import com.studytick.app.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务：维持学习计时，持续刷新常驻通知（锁屏可见）。
 */
class StudyTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private lateinit var repo: StudyRepository

    override fun onCreate() {
        super.onCreate()
        repo = application.studyApp.repo
        Notifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TimerActionReceiver.ACTION_STOP) {
            scope.launch {
                TimerController.stop(this@StudyTimerService)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        goForeground(Notifications.placeholder(this))

        if (loop?.isActive != true) {
            loop = scope.launch {
                var tick = 0
                while (isActive) {
                    val state = repo.snapshotToday()
                    if (!state.running) {
                        stopSelf()
                        break
                    }
                    goForeground(Notifications.build(this@StudyTimerService, state))
                    if (tick % 6 == 5) repo.reconcileDay()
                    if (tick % 18 == 17) WidgetRefresh.refresh(this@StudyTimerService)
                    tick++
                    delay(10_000L)
                }
            }
        }
        return START_STICKY
    }

    private fun goForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                Notifications.NOTIFICATION_ID,
                notification,
                type
            )
        } else {
            startForeground(Notifications.NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
