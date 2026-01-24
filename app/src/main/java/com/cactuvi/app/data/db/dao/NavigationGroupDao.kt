package com.cactuvi.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cactuvi.app.data.db.entities.NavigationGroupEntity

@Dao
interface NavigationGroupDao {
    
    @Query("SELECT * FROM navigation_groups WHERE type = :type ORDER BY groupName ASC")
    suspend fun getByType(type: String): List<NavigationGroupEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(groups: List<NavigationGroupEntity>)
    
    @Query("DELETE FROM navigation_groups WHERE type = :type")
    suspend fun deleteByType(type: String)
    
    @Query("DELETE FROM navigation_groups")
    suspend fun clear()
}
