package com.studytick.app.util

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object TimeFmt {

    /** 01:23:45 */
    fun hms(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return "%02d:%02d:%02d".format(h, m, sec)
    }

    /** 1:23（小时:分钟），不足一小时则 23 */
    fun hm(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "%d:%02d".format(h, m) else "$m"
    }

    /** 桌面小组件：小于 1 小时用 mm:ss，否则 h:mm */
    fun widget(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        return if (s >= 3600) {
            "%d:%02d".format(s / 3600, (s % 3600) / 60)
        } else {
            "%02d:%02d".format(s / 60, s % 60)
        }
    }

    /** 组件的辅助单位文案 */
    fun widgetUnit(totalSeconds: Int): String = if (totalSeconds >= 3600) "剩余小时" else "剩余分钟"

    /** 2 小时 30 分 */
    fun human(totalMinutes: Int): String {
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "$h 小时 $m 分"
            h > 0 -> "$h 小时"
            else -> "$m 分钟"
        }
    }

    private val WEEK_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekdayCn(date: LocalDate): String = WEEK_CN[date.dayOfWeek.value - 1]

    fun monthTitle(date: LocalDate): String = "${date.year} 年 ${date.monthValue} 月"

    fun dateCn(date: LocalDate): String = "${date.monthValue} 月 ${date.dayOfMonth} 日"
}
