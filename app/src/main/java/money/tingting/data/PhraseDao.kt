package money.tingting.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhraseDao {
    @Query("SELECT * FROM phrases WHERE type = :type ORDER BY `order` ASC")
    fun getPhrasesByType(type: String): Flow<List<PhraseEntity>>

    @Query("SELECT * FROM phrases ORDER BY type, `order`")
    fun getAllPhrases(): Flow<List<PhraseEntity>>

    @Insert
    suspend fun insert(phrase: PhraseEntity)

    @Update
    suspend fun update(phrase: PhraseEntity)

    @Delete
    suspend fun delete(phrase: PhraseEntity)

    @Query("UPDATE phrases SET isEnabled = :enabled WHERE type = :type")
    suspend fun updateTypeEnabled(type: String, enabled: Boolean)
}
