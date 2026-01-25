package com.cactuvi.app.data.db

import androidx.room.*
import com.cactuvi.app.data.db.entities.StreamSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StreamSourceDao {

    @Query("SELECT * FROM stream_sources ORDER BY createdAt DESC")
    suspend fun getAll(): List<StreamSourceEntity>

    @Query("SELECT * FROM stream_sources WHERE id = :id")
    suspend fun getById(id: String): StreamSourceEntity?

    @Query("SELECT * FROM stream_sources WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): StreamSourceEntity?

    @Query("SELECT * FROM stream_sources WHERE isActive = 1 LIMIT 1")
    fun getActiveFlow(): Flow<StreamSourceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(source: StreamSourceEntity)

    @Update suspend fun update(source: StreamSourceEntity)

    @Delete suspend fun delete(source: StreamSourceEntity)

    @Query("UPDATE stream_sources SET isActive = 0") suspend fun setAllInactive()

    @Transaction
    suspend fun setActive(id: String) {
        setAllInactive()
        setActiveById(id)
        updateLastUsed(id, System.currentTimeMillis())
    }

    @Query("UPDATE stream_sources SET isActive = 1 WHERE id = :id")
    suspend fun setActiveById(id: String)

    @Query("UPDATE stream_sources SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long)
}
