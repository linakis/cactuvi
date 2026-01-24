package com.cactuvi.app.data.db.dao

import androidx.room.*
import com.cactuvi.app.data.db.entities.FavoriteEntity

@Dao
interface FavoriteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)
    
    @Delete
    suspend fun delete(favorite: FavoriteEntity)
    
    @Query("SELECT * FROM favorites WHERE sourceId = :sourceId ORDER BY addedAt DESC")
    suspend fun getAll(sourceId: String): List<FavoriteEntity>
    
    @Query("SELECT * FROM favorites WHERE sourceId = :sourceId AND contentType = :type ORDER BY addedAt DESC")
    suspend fun getByType(sourceId: String, type: String): List<FavoriteEntity>
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE sourceId = :sourceId AND id = :id)")
    suspend fun isFavorite(sourceId: String, id: String): Boolean
    
    @Query("DELETE FROM favorites WHERE sourceId = :sourceId AND id = :id")
    suspend fun deleteById(sourceId: String, id: String)
    
    @Query("DELETE FROM favorites WHERE sourceId = :sourceId")
    suspend fun clearBySource(sourceId: String)
    
    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
