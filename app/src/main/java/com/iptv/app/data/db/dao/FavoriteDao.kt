package com.iptv.app.data.db.dao

import androidx.room.*
import com.iptv.app.data.db.entities.FavoriteEntity

@Dao
interface FavoriteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)
    
    @Delete
    suspend fun delete(favorite: FavoriteEntity)
    
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun getAll(): List<FavoriteEntity>
    
    @Query("SELECT * FROM favorites WHERE contentType = :type ORDER BY addedAt DESC")
    suspend fun getByType(type: String): List<FavoriteEntity>
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun isFavorite(id: String): Boolean
    
    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}
