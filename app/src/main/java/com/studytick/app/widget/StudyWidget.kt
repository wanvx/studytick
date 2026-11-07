package com.studytick.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.studytick.app.MainActivity
import com.studytick.app.R
import com.studytick.app.data.TodayState
import com.studytick.app.studyApp
import com.studytick.app.timer.TimerActionReceiver
import com.studytick.app.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 2x2 桌面小组件：今日状态 + 剩余时间 + 进度 + 一键开始/结束。
 *
 * 使用经典 AppWidgetProvider + RemoteViews XML 布局实现（不依赖 Glance），
 * 计时中通过 Chronometer 由系统逐秒自动倒计时，无需频繁刷新。
 */
class StudyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val state = appContext.studyApp.repo.snapshotToday()
                val views = buildWidgetViews(appContext, state)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } catch (t: Throwable) {
                android.util.Log.w("StudyWidgetProvider", "onUpdate failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}

/** 小组件刷新的统一入口：App 内计时开始/结束、前台服务、WorkManager 都调用。 */
object WidgetRefresh {

    suspend fun refresh(context: Context) {
        runCatching {
            val state = context.studyApp.repo.snapshotToday()
            updateAll(context, state)
        }
    }

    fun updateAll(context: Context, state: TodayState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, StudyWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = buildWidgetViews(context, state)
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

private fun buildWidgetViews(context: Context, state: TodayState): RemoteViews {
    val rv = RemoteViews(context.packageName, R.layout.widget_study)

    val status = when {
        state.completed -> "已完成今日打卡"
        state.running -> "学习中"
        state.studiedSeconds > 0 -> "今日进行中"
        else -> "未开始"
    }
    rv.setTextViewText(R.id.widget_status, status)

    if (state.running && !state.completed) {
        // 计时中：Chronometer 倒计时，系统每秒自动刷新，省电且流畅
        rv.setViewVisibility(R.id.widget_time, View.GONE)
        rv.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
        rv.setChronometerCountDown(R.id.widget_chrono, true)
        val base = SystemClock.elapsedRealtime() + state.remainingSeconds * 1000L
        rv.setChronometer(R.id.widget_chrono, base, null, true)
    } else {
        rv.setViewVisibility(R.id.widget_chrono, View.GONE)
        rv.setViewVisibility(R.id.widget_time, View.VISIBLE)
        rv.setTextViewText(
            R.id.widget_time,
            if (state.completed) "00:00" else TimeFmt.widget(state.remainingSeconds),
        )
    }

    rv.setProgressBar(R.id.widget_progress, 100, (state.progress * 100).toInt(), false)

    rv.setTextViewText(
        R.id.widget_studied,
        when {
            state.completed -> "已学 ${state.studiedSeconds / 60} 分"
            state.studiedSeconds <= 0 -> "还未开始"
            else -> "已学 ${state.studiedSeconds / 60} 分"
        },
    )

    rv.setTextViewText(
        R.id.widget_action,
        when {
            state.completed -> "已完成"
            state.running -> "结束"
            else -> "开始"
        },
    )

    // 点击卡片整体：打开 App
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    rv.setOnClickPendingIntent(R.id.widget_root, pendingActivity(context, openIntent))

    // 点击胶囊按钮：开始 / 结束
    val toggleIntent = Intent(context, TimerActionReceiver::class.java)
        .setAction(TimerActionReceiver.ACTION_TOGGLE)
    rv.setOnClickPendingIntent(R.id.widget_action, pendingBroadcast(context, toggleIntent))

    return rv
}

private fun pendingActivity(context: Context, intent: Intent): PendingIntent =
    PendingIntent.getActivity(context, REQ_OPEN, intent, PI_FLAGS)

private fun pendingBroadcast(context: Context, intent: Intent): PendingIntent =
    PendingIntent.getBroadcast(context, REQ_TOGGLE, intent, PI_FLAGS)

private const val REQ_OPEN = 101
private const val REQ_TOGGLE = 102
private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
