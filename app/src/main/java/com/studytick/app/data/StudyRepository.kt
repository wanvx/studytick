package com.studytick.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 今日学习状态（实时推算，不依赖数据库每秒写入）。 */
data class TodayState(
    val date: LocalDate,
    val studiedSeconds: Int,
    val goalSeconds: Int,
    val running: Boolean,
    val completed: Boolean,
    /** 本周（周一 00:00 起）已学秒数，含进行中的会话 */
    val weekStudiedSeconds: Int = 0,
    /** 本周计划秒数（个人页设置的周目标） */
    val weekGoalSeconds: Int = 0,
) {
    val remainingSeconds: Int get() = (goalSeconds - studiedSeconds).coerceAtLeast(0)
    val progress: Float
        get() = if (goalSeconds <= 0) 0f else (studiedSeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)

    val weekRemainingSeconds: Int get() = (weekGoalSeconds - weekStudiedSeconds).coerceAtLeast(0)
    val weekProgress: Float
        get() = if (weekGoalSeconds <= 0) 0f
        else (weekStudiedSeconds.toFloat() / weekGoalSeconds).coerceIn(0f, 1f)
    val weekCompleted: Boolean get() = weekGoalSeconds > 0 && weekStudiedSeconds >= weekGoalSeconds
}

class StudyRepository(
    private val dao: DayRecordDao,
    private val prefs: Prefs,
) {

    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000L)
        }
    }

    val goalMinutes: Flow<Int> = prefs.snapshot.map { it.goalMinutes }.distinctUntilChanged()

    val weeklyGoalHours: Flow<Int> = prefs.snapshot.map { it.weeklyGoalHours }.distinctUntilChanged()

    fun observeRecords(): Flow<List<DayRecord>> = dao.observeAll()

    fun observeToday(): Flow<TodayState> =
        combine(ticker, dao.observeAll(), prefs.snapshot) { now, records, snap ->
            buildToday(now, records, snap)
        }

    suspend fun snapshotToday(now: Long = System.currentTimeMillis()): TodayState =
        buildToday(now, dao.getAll(), prefs.snapshot.first())

    private fun buildToday(now: Long, records: List<DayRecord>, snap: PrefsSnapshot): TodayState {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val record = records.firstOrNull { it.date == today.toString() }
        val goal = snap.goalMinutes * 60
        val liveSession = snap.sessionStartAt > 0L && snap.sessionDate == today.toString()
        val base = if (liveSession) snap.sessionBaseSeconds else (record?.studiedSeconds ?: 0)
        val elapsed =
            if (liveSession) ((now - snap.sessionStartAt) / 1000L).toInt().coerceAtLeast(0) else 0
        val studied = (base + elapsed).coerceAtMost(MAX_DAY_SECONDS)

        // 本周：周一 00:00 起。今天的记录可能落后于实时值（会话中不逐秒写库），
        // 所以今天用 studied，其余日期用库里的值。
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        val todayKey = today.toString()
        val weekBefore = records
            .filter { it.date >= weekStart && it.date < todayKey }
            .sumOf { it.studiedSeconds }
        val weekStudied = weekBefore + studied

        return TodayState(
            date = today,
            studiedSeconds = studied,
            goalSeconds = goal,
            running = liveSession,
            completed = studied >= goal,
            weekStudiedSeconds = weekStudied,
            weekGoalSeconds = snap.weeklyGoalHours * 3600,
        )
    }

    /** 开始一次学习计时。 */
    suspend fun startSession(now: Long = System.currentTimeMillis()) {
        val snap = prefs.snapshot.first()
        if (snap.sessionStartAt > 0L) return
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()
        val existing = dao.get(today)?.studiedSeconds ?: 0
        val goal = snap.goalMinutes * 60
        dao.upsert(DayRecord(today, existing, goal, existing >= goal))
        prefs.beginSession(today, existing, now)
    }

    /** 结束计时并落库，跨天时按当日 23:59:59 截断。 */
    suspend fun stopSession(now: Long = System.currentTimeMillis()) {
        val snap = prefs.snapshot.first()
        if (snap.sessionStartAt <= 0L) return
        val zone = ZoneId.systemDefault()
        val sessionDay = runCatching { LocalDate.parse(snap.sessionDate) }.getOrNull()
        if (sessionDay != null) {
            val dayEnd = sessionDay.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
            val effective = minOf(now, dayEnd)
            val elapsed = ((effective - snap.sessionStartAt) / 1000L).toInt().coerceAtLeast(0)
            val studied = (snap.sessionBaseSeconds + elapsed).coerceAtMost(MAX_DAY_SECONDS)
            val goal = snap.goalMinutes * 60
            val alreadyCompleted = dao.get(snap.sessionDate)?.completed ?: false
            dao.upsert(
                DayRecord(snap.sessionDate, studied, goal, alreadyCompleted || studied >= goal)
            )
        }
        prefs.clearSession()
    }

    suspend fun setGoalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES)
        prefs.setGoalMinutes(clamped)
        val today = LocalDate.now().toString()
        val record = dao.get(today) ?: return
        dao.upsert(
            DayRecord(
                date = today,
                studiedSeconds = record.studiedSeconds,
                goalSeconds = clamped * 60,
                completed = record.completed || record.studiedSeconds >= clamped * 60,
            )
        )
    }

    /** 设置每周计划小时数（个人页 ±1 小时）。只影响每周进度展示，不改历史记录。 */
    suspend fun setWeeklyGoalHours(hours: Int) {
        prefs.setWeeklyGoalHours(hours.coerceIn(MIN_WEEKLY_GOAL_HOURS, MAX_WEEKLY_GOAL_HOURS))
    }

    /** 清除全部打卡数据（保留每日目标与每周计划设置）。 */
    suspend fun clearRecords() {
        if (prefs.snapshot.first().sessionStartAt > 0L) stopSession()
        dao.clearAll()
    }

    /** 跨天后自动结束上一次计时，避免计时一直累计到新的一天。 */
    suspend fun reconcileDay(now: Long = System.currentTimeMillis()) {
        val snap = prefs.snapshot.first()
        if (snap.sessionStartAt <= 0L) return
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        if (snap.sessionDate != today) stopSession(now)
    }

    companion object {
        const val MIN_GOAL_MINUTES = 5
        const val MAX_GOAL_MINUTES = 720
        const val MIN_WEEKLY_GOAL_HOURS = 1
        const val MAX_WEEKLY_GOAL_HOURS = 100
        private const val MAX_DAY_SECONDS = 20 * 3600
    }
}
