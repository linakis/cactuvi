package com.cactuvi.app.data.db.dao

import androidx.room.*
import com.cactuvi.app.data.db.entities.CategoryEntity

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY categoryName ASC")
    suspend fun getAllByType(type: String): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE type = :type") suspend fun clearByType(type: String)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("DELETE FROM categories") suspend fun clearAll()
}
