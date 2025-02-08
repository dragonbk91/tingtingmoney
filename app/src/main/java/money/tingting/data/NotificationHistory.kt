package money.tingting.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "noti_histories")
data class NotificationHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val created_at: Date = Date(),
    val package_name: String,
    val bank: String,
    val time: Date,
    val amount: Long,
    val title: String,
    val text: String
)
