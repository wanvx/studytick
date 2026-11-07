package com.studytick.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.studytick.app.studyApp
import com.studytick.app.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机后恢复计时会话：如果上次退出时仍在计时，重新拉起前台服务，
 * 否则锁屏与通知栏会一直看不到剩余时间（状态还在，但服务没了）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val repo = appContext.studyApp.repo
                repo.reconcileDay()
                if (repo.snapshotToday().running) {
                    TimerController.startTimerService(appContext)
                    WidgetRefresh.refresh(appContext)
                }
            } catch (t: Throwable) {
                Log.w("BootReceiver", "restore session failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
