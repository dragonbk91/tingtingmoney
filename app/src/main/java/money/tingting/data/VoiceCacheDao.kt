package money.tingting.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface VoiceCacheDao {
    @Query("SELECT * FROM voice_cache WHERE text = :text AND voice = :voice")
    fun getByTextAndVoice(text: String, voice: String): Flow<VoiceCache?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voiceCache: VoiceCache): Long

    @Delete
    suspend fun delete(voiceCache: VoiceCache)

    @Query("DELETE FROM voice_cache WHERE createdAt < :date")
    suspend fun deleteOlderThan(date: Date)
}