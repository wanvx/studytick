package com.studytick.app.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studytick.app.StudyApp
import com.studytick.app.data.DayRecord
import com.studytick.app.data.PrefsSnapshot
import com.studytick.app.data.TodayState
import com.studytick.app.timer.TimerController
import com.studytick.app.update.UpdateChecker
import com.studytick.app.update.UpdateResult
import com.studytick.app.widget.WidgetRefresh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object Latest : UpdateUi
    data class Available(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
    ) : UpdateUi

    data class Failed(val message: String) : UpdateUi
}

class StudyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as StudyApp).repo

    val today: StateFlow<TodayState> = repo.observeToday().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(3000),
        TodayState(LocalDate.now(), 0, PrefsSnapshot.DEFAULT_GOAL_MINUTES * 60, false, false),
    )

    val records: StateFlow<List<DayRecord>> = repo.observeRecords().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(3000),
        emptyList(),
    )

    val goalMinutes: StateFlow<Int> = repo.goalMinutes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(3000),
        PrefsSnapshot.DEFAULT_GOAL_MINUTES,
    )

    val weeklyGoalHours: StateFlow<Int> = repo.weeklyGoalHours.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(3000),
        PrefsSnapshot.DEFAULT_WEEKLY_GOAL_HOURS,
    )

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    init {
        viewModelScope.launch {
            repo.reconcileDay()
            WidgetRefresh.refresh(getApplication())
        }
    }

    fun toggleTimer() {
        viewModelScope.launch {
            TimerController.toggle(getApplication())
        }
    }

    fun autoStart() {
        viewModelScope.launch {
            if (!repo.snapshotToday().running) TimerController.start(getApplication())
        }
    }

    fun setGoal(minutes: Int) {
        viewModelScope.launch {
            repo.setGoalMinutes(minutes)
            WidgetRefresh.refresh(getApplication())
        }
    }

    fun setWeeklyGoal(hours: Int) {
        viewModelScope.launch {
            repo.setWeeklyGoalHours(hours)
            WidgetRefresh.refresh(getApplication())
        }
    }

    fun clearData() {
        viewModelScope.launch {
            val state = repo.snapshotToday()
            if (state.running) TimerController.stop(getApplication())
            repo.clearRecords()
            WidgetRefresh.refresh(getApplication())
        }
    }

    fun checkUpdate() {
        _update.value = UpdateUi.Checking
        viewModelScope.launch {
            _update.value = when (val result = UpdateChecker.check()) {
                is UpdateResult.UpToDate -> UpdateUi.Latest
                is UpdateResult.Failed -> UpdateUi.Failed(result.message)
                is UpdateResult.Available -> UpdateUi.Available(
                    version = result.info.versionName,
                    releaseUrl = result.info.releaseUrl,
                    apkUrl = result.info.apkUrl,
                )
            }
        }
    }

    fun pinWidget() {
        val context = getApplication<Application>()
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
        if (manager.isRequestPinAppWidgetSupported) {
            runCatching {
                manager.requestPinAppWidget(
                    ComponentName(context, com.studytick.app.widget.StudyWidgetProvider::class.java),
                    null,
                    null,
                )
            }
        }
    }

    fun onForeground() {
        viewModelScope.launch {
            repo.reconcileDay()
            // 会话仍在进行，但进程可能被系统回收过：前台服务和通知都会消失，
            // 导致「界面显示专注中、锁屏却什么都没有」，这里补启一次（幂等）
            val state = repo.snapshotToday()
            if (state.running) TimerController.startTimerService(getApplication())
            WidgetRefresh.refresh(getApplication())
        }
    }
}
