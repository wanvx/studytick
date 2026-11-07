package com.studytick.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.studytick.app.MainActivity
import com.studytick.app.R
import com.studytick.app.data.TodayState
import com.studytick.app.util.TimeFmt

/**
 * 常驻通知：锁屏上显示「已学 / 剩余」，并用倒计时 chronometer 走秒。
 */
object Notifications {

    const val CHANNEL_ID = "study_timer"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "学习计时",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在锁屏上显示今日学习进度"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun placeholder(context: Context): Notification {
        ensureChannel(context)
        return base(context)
            .setContentTitle("正在准备计时")
            .setContentText(" ")
            .build()
    }

    fun build(context: Context, state: TodayState): Notification {
        ensureChannel(context)
        val title = if (state.completed) "今日已达标" else "学习中"
        val text = if (state.completed) {
            "已完成今日打卡 · 已学 ${TimeFmt.hms(state.studiedSeconds)}"
        } else {
            "已学 ${TimeFmt.hms(state.studiedSeconds)} · 剩余 ${TimeFmt.hms(state.remainingSeconds)}"
        }

        val builder = base(context)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("今日目标 ${TimeFmt.hms(state.goalSeconds)}")
            .setProgress(
                state.goalSeconds.coerceAtLeast(1),
                state.studiedSeconds.coerceAtMost(state.goalSeconds.coerceAtLeast(1)),
                false
            )
            .addAction(0, "结束学习", stopIntent(context))

        if (!state.completed) {
            builder.setWhen(System.currentTimeMillis() + state.remainingSeconds * 1000L)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        }

        return builder.build()
    }

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_study)
            .setColor(0xFF2F6BFF.toInt())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    private fun openIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerActionReceiver::class.java).apply {
            action = TimerActionReceiver.ACTION_STOP
        }
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
