package money.tingting.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface NotificationHistoryDao {
    @Query("SELECT * FROM noti_histories ORDER BY created_at DESC")
    fun getAllHistories(): Flow<List<NotificationHistory>>

    @Query("SELECT * FROM noti_histories WHERE time BETWEEN :startDate AND :endDate ORDER BY time ASC")
    fun getHistoriesBetweenDates(startDate: Date, endDate: Date): Flow<List<NotificationHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: NotificationHistory)

    @Delete
    suspend fun delete(history: NotificationHistory)

    @Query("DELETE FROM noti_histories")
    suspend fun deleteAll()
}
