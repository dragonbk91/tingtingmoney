
package money.tingting.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "voice_cache")
data class VoiceCache(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val voice: String,
    val localPath: String?,
    val fptLink: String?,
    val cdnLink: String?,
    val createdAt: Date = Date()
)