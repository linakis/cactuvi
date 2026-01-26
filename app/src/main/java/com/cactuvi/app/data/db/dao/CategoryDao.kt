package com.cactuvi.app.data.db.dao

import androidx.room.*
import com.cactuvi.app.data.db.entities.CategoryEntity

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY categoryName ASC")
    suspend fun getAllByType(type: String): List<CategoryEntity>

    // NEW: Get top-level categories (parentId = 0)
    @Query(
        """
        SELECT * FROM categories
        WHERE type = :type AND parentId = 0
        ORDER BY categoryName ASC
    """
    )
    suspend fun getTopLevel(type: String): List<CategoryEntity>

    // NEW: Get children of specific category
    @Query(
        """
        SELECT * FROM categories
        WHERE type = :type AND parentId = :parentCategoryId
        ORDER BY categoryName ASC
    """
    )
    suspend fun getChildren(type: String, parentCategoryId: Int): List<CategoryEntity>

    // NEW: Get single category by ID
    @Query(
        """
        SELECT * FROM categories
        WHERE type = :type AND categoryId = :categoryId
        LIMIT 1
    """
    )
    suspend fun getById(type: String, categoryId: String): CategoryEntity?

    // NEW: Update children count
    @Query(
        """
        UPDATE categories
        SET childrenCount = :count
        WHERE type = :type AND categoryId = :categoryId
    """
    )
    suspend fun updateChildrenCount(type: String, categoryId: String, count: Int)

    // NEW: Batch update isLeaf flag
    @Query(
        """
        UPDATE categories
        SET isLeaf = :isLeaf
        WHERE type = :type AND categoryId IN (:categoryIds)
    """
    )
    suspend fun updateIsLeaf(type: String, categoryIds: List<String>, isLeaf: Boolean)

    @Query("DELETE FROM categories WHERE type = :type") suspend fun clearByType(type: String)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: String)

    @Query("DELETE FROM categories") suspend fun clearAll()
}
