package studio.ainovations.callguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Room data access for [RuleEntity]. Rules are always read in [RuleEntity.position]
 * order so declaration order — which [RuleCompiler][studio.ainovations.callguard.domain.RuleCompiler]
 * uses to break priority/specificity ties — survives a round trip through
 * SQLite.
 */
@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY position ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules ORDER BY position ASC")
    suspend fun getAllOnce(): List<RuleEntity>

    @Insert
    suspend fun insertAll(entities: List<RuleEntity>)

    @Query("DELETE FROM rules")
    suspend fun deleteAll()

    /**
     * Replace every persisted rule with [entities] atomically: a reader
     * (e.g. [observeAll]) never sees an empty table between the delete and
     * the insert. [RuleRepository] additionally compiles and publishes a
     * snapshot only after this completes, so an evaluator never observes a
     * partially applied rule set either.
     */
    @Transaction
    suspend fun replaceAll(entities: List<RuleEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
