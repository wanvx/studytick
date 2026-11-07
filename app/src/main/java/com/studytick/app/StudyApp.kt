package com.studytick.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.studytick.app.data.AppDatabase
import com.studytick.app.data.Prefs
import com.studytick.app.data.StudyRepository
import com.studytick.app.widget.WidgetWorker
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

    private fun scheduleWidgetRefresh() {
        val request = PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("widget_refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        lateinit var instance: StudyApp
            private set
    }
}

val android.content.Context.studyApp: StudyApp
    get() = applicationContext as StudyApp
