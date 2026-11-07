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
 *
 * 关于锁屏可见性：
 * IMPORTANCE_LOW 的渠道会被系统归入「静默通知」，Android 11 起锁屏默认不展示静默通知，
 * 因此这里用 IMPORTANCE_DEFAULT + 无声音无震动，既能在锁屏露出，又不会打扰。
 * 渠道重要性创建后不可修改，故旧渠道（study_timer）需要删除并用新 id 重建。
 */
object Notifications {

    const val CHANNEL_ID = "study_timer_v2"
    private const val LEGACY_CHANNEL_ID = "study_timer"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // 清掉旧版本创建的低重要性渠道，避免设置里出现两个同名「学习计时」
        if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
            runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
        }

        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "学习计时",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "计时中在通知栏与锁屏显示今日学习进度"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            enableLights(false)
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

        val done = state.completed
        val title = when {
            done -> "今日已达标"
            state.studiedSeconds > 0 -> "学习中"
            else -> "刚开始学习"
        }

        val builder = base(context)
            // 标题行会与倒计时同排，优先放「状态 + 已学」，锁屏一眼就能同时看到已学与剩余
            .setContentTitle("$title · 已学 ${TimeFmt.hms(state.studiedSeconds)}")
            .setContentText(
                if (done) "今日目标 ${TimeFmt.hms(state.goalSeconds)} 已达成"
                else "剩余 ${TimeFmt.hms(state.remainingSeconds)}"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (done) {
                        "已完成今日打卡 · 已学 ${TimeFmt.hms(state.studiedSeconds)}"
                    } else {
                        "已学 ${TimeFmt.hms(state.studiedSeconds)} · 剩余 ${TimeFmt.hms(state.remainingSeconds)}\n" +
                            "今日目标 ${TimeFmt.hms(state.goalSeconds)}"
                    }
                )
            )
            .setProgress(
                state.goalSeconds.coerceAtLeast(1),
                state.studiedSeconds.coerceAtMost(state.goalSeconds.coerceAtLeast(1)),
                false
            )
            .addAction(0, "结束学习", stopIntent(context))

        if (!done) {
            builder.setWhen(System.currentTimeMillis() + state.remainingSeconds * 1000L)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.setShortcutId("study_timer")
        }

        return builder.build()
    }

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_study)
            .setColor(0xFF2F6BFF.toInt())
            .setOngoing(true)
            // 注意：不要用 setSilent(true)，它会把通知降到静默分区，锁屏就不显示了
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(0)
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
