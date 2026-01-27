package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.entities.toEntity
import com.cactuvi.app.data.models.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class SourceManager
@Inject
constructor(@ApplicationContext context: Context, private val database: AppDatabase) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("source_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACTIVE_SOURCE_ID = "active_source_id"
    }

    // ========== PUBLIC API ==========

    suspend fun getAllSources(): List<StreamSource> =
        withContext(Dispatchers.IO) { database.streamSourceDao().getAll().map { it.toModel() } }

    suspend fun getActiveSource(): StreamSource? =
        withContext(Dispatchers.IO) { database.streamSourceDao().getActive()?.toModel() }

    fun getActiveSourceFlow(): Flow<StreamSource?> {
        return database.streamSourceDao().getActiveFlow().map { it?.toModel() }
    }

    suspend fun setActiveSource(id: String) =
        withContext(Dispatchers.IO) {
            database.streamSourceDao().setActive(id)
            prefs.edit().putString(KEY_ACTIVE_SOURCE_ID, id).apply()
        }

    suspend fun addSource(source: StreamSource) =
        withContext(Dispatchers.IO) { database.streamSourceDao().insert(source.toEntity()) }

    suspend fun updateSource(source: StreamSource) =
        withContext(Dispatchers.IO) { database.streamSourceDao().update(source.toEntity()) }

    suspend fun deleteSource(id: String) =
        withContext(Dispatchers.IO) {
            val source = database.streamSourceDao().getById(id)
            if (source != null) {
                database.streamSourceDao().delete(source)
            }
        }
}
