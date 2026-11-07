package com.studytick.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 今日学习状态（实时推算，不依赖数据库每秒写入）。 */
data class TodayState(
    val date: LocalDate,
    val studiedSeconds: Int,
    val goalSeconds: Int,
    val running: Boolean,
    val completed: Boolean,
) {
    val remainingSeconds: Int get() = (goalSeconds - studiedSeconds).coerceAtLeast(0)
    val progress: Float
        get() = if (goalSeconds <= 0) 0f else (studiedSeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
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
        return TodayState(today, studied, goal, liveSession, studied >= goal)
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

    /** 清除全部打卡数据（保留每日目标设置）。 */
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
        private const val MAX_DAY_SECONDS = 20 * 3600
    }
}
