package com.studytick.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.studytick.app.data.AppDatabase
import com.studytick.app.data.Prefs
import com.studytick.app.data.StudyRepository
import com.studytick.app.widget.WidgetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class StudyApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val prefs: Prefs by lazy { Prefs(this) }
    val repo: StudyRepository by lazy { StudyRepository(database.dayRecordDao(), prefs) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        scheduleWidgetRefresh()
    }

    /**
     * 小组件兜底刷新（15 分钟一次）。
     *
     * WorkManager 首次初始化会建一个独立的 Room 数据库，原先由 androidx.startup 在主线程执行，
     * 冷启动时能把主线程堵住十几秒（已在模拟器上抓到 ANR）。这里改成后台线程初始化，
     * 启动路径上不做任何 IO。
     */
    private fun scheduleWidgetRefresh() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                runCatching {
                    WorkManager.initialize(
                        this@StudyApp,
                        Configuration.Builder().setMinimumLoggingLevel(Log.ERROR).build(),
                    )
                }
                val request =
                    PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(this@StudyApp)
                    .enqueueUniquePeriodicWork(
                        "widget_refresh",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request,
                    )
            }.onFailure { Log.w("StudyApp", "work manager init failed", it) }
        }
    }

    companion object {
        lateinit var instance: StudyApp
            private set
    }
}

val android.content.Context.studyApp: StudyApp
    get() = applicationContext as StudyApp
