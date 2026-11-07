package com.studytick.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "day_records")
data class DayRecord(
    @PrimaryKey val date: String,
    val studiedSeconds: Int,
    val goalSeconds: Int,
    val completed: Boolean,
)

@Dao
interface DayRecordDao {

    @Query("SELECT * FROM day_records ORDER BY date ASC")
    fun observeAll(): Flow<List<DayRecord>>

    @Query("SELECT * FROM day_records ORDER BY date ASC")
    suspend fun getAll(): List<DayRecord>

    @Query("SELECT * FROM day_records WHERE date = :date LIMIT 1")
    suspend fun get(date: String): DayRecord?

    @Query("SELECT * FROM day_records WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    fun observeRange(from: String, to: String): Flow<List<DayRecord>>

    @Upsert
    suspend fun upsert(record: DayRecord)

    @Query("DELETE FROM day_records")
    suspend fun clearAll()
}

@Database(entities = [DayRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dayRecordDao(): DayRecordDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "studytick.db").build()
    }
}
