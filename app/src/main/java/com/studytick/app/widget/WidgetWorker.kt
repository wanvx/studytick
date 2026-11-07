package com.studytick.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.studytick.app.studyApp

class WidgetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.studyApp
        runCatching { app.repo.reconcileDay() }
        WidgetRefresh.refresh(applicationContext)
        return Result.success()
    }
}
