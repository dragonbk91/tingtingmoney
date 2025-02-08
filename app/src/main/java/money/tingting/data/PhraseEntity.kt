package money.tingting.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phrases")
data class PhraseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String, // "opening", "busy", "random"
    val content: String,
    val isEnabled: Boolean = true,
    val order: Int = 0
)
