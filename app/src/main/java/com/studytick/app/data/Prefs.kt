package com.studytick.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studytick_prefs")

data class PrefsSnapshot(
    val goalMinutes: Int = DEFAULT_GOAL_MINUTES,
    val sessionStartAt: Long = 0L,
    val sessionDate: String = "",
    val sessionBaseSeconds: Int = 0,
) {
    val isRunning: Boolean get() = sessionStartAt > 0L

    companion object {
        const val DEFAULT_GOAL_MINUTES = 120
    }
}

class Prefs(private val context: Context) {

    private object Keys {
        val GOAL = intPreferencesKey("goal_minutes")
        val SESSION_START = longPreferencesKey("session_start_at")
        val SESSION_DATE = stringPreferencesKey("session_date")
        val SESSION_BASE = intPreferencesKey("session_base_seconds")
    }

    val snapshot: Flow<PrefsSnapshot> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { p ->
            PrefsSnapshot(
                goalMinutes = p[Keys.GOAL] ?: PrefsSnapshot.DEFAULT_GOAL_MINUTES,
                sessionStartAt = p[Keys.SESSION_START] ?: 0L,
                sessionDate = p[Keys.SESSION_DATE] ?: "",
                sessionBaseSeconds = p[Keys.SESSION_BASE] ?: 0,
            )
        }

    suspend fun setGoalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.GOAL] = minutes }
    }

    suspend fun beginSession(date: String, baseSeconds: Int, startAt: Long) {
        context.dataStore.edit {
            it[Keys.SESSION_DATE] = date
            it[Keys.SESSION_BASE] = baseSeconds
            it[Keys.SESSION_START] = startAt
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.SESSION_START)
            it.remove(Keys.SESSION_DATE)
            it.remove(Keys.SESSION_BASE)
        }
    }
}
