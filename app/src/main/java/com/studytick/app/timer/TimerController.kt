package com.studytick.app.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.studytick.app.MainActivity
import com.studytick.app.studyApp
import com.studytick.app.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 计时器的统一入口：App、通知栏按钮、桌面小组件都走这里。 */
object TimerController {

    suspend fun start(context: Context) {
        val app = context.studyApp
        app.repo.startSession()
        if (!startTimerService(context)) {
            launchApp(context, autoStart = true)
        }
        WidgetRefresh.refresh(context)
    }

    suspend fun stop(context: Context) {
        val app = context.studyApp
        app.repo.stopSession()
        context.stopService(Intent(context, StudyTimerService::class.java))
        WidgetRefresh.refresh(context)
    }

    suspend fun toggle(context: Context) {
        val running = context.studyApp.repo.snapshotToday().running
        if (running) stop(context) else start(context)
    }

    /**
     * 从桌面小组件 / 通知在后台启动前台服务。Android 12+ 在后台启动前台服务可能被拒绝，
     * 此时退化为打开 App 并自动开始计时。
     */
    fun startTimerService(context: Context): Boolean = try {
        ContextCompat.startForegroundService(
            context,
            Intent(context, StudyTimerService::class.java)
        )
        true
    } catch (t: Throwable) {
        false
    }

    fun launchApp(context: Context, autoStart: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_AUTO_START, autoStart)
        }
        context.startActivity(intent)
    }

    fun updateWidgetAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch { WidgetRefresh.refresh(appContext) }
    }
}
