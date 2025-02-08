package money.tingting

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.Flow
import money.tingting.data.AppDatabase
import money.tingting.data.NotificationHistory
import java.util.*

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    fun getHistoryData(days: Int): Flow<List<NotificationHistory>> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startDate = calendar.time

        return db.notificationHistoryDao().getHistoriesBetweenDates(startDate, endDate)
    }
}