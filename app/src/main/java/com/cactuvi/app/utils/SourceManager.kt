package com.cactuvi.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.cactuvi.app.data.db.AppDatabase
import com.cactuvi.app.data.db.entities.toEntity
import com.cactuvi.app.data.models.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SourceManager private constructor(context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("source_prefs", Context.MODE_PRIVATE)
    private val credentialsManager = CredentialsManager.getInstance(context)

    companion object {
        @Volatile private var INSTANCE: SourceManager? = null

        fun getInstance(context: Context): SourceManager {
            return INSTANCE
                ?: synchronized(this) {
                    val instance = SourceManager(context.applicationContext)
                    INSTANCE = instance
                    instance
                }
        }

        private const val KEY_ACTIVE_SOURCE_ID = "active_source_id"
        private const val KEY_MIGRATED = "has_migrated_credentials"
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

    suspend fun migrateCurrentCredentialsToSource() =
        withContext(Dispatchers.IO) {
            if (prefs.getBoolean(KEY_MIGRATED, false)) {
                return@withContext // Already migrated
            }

            val server = credentialsManager.getServer()
            val username = credentialsManager.getUsername()
            val password = credentialsManager.getPassword()

            // Only migrate if credentials exist
            if (server.isNotEmpty() && username.isNotEmpty()) {
                val defaultSource =
                    StreamSource(
                        id = "default",
                        nickname = "Default Provider",
                        server = server,
                        username = username,
                        password = password,
                        isActive = true,
                        isPrimary = true,
                    )

                database.streamSourceDao().insert(defaultSource.toEntity())
                prefs
                    .edit()
                    .putString(KEY_ACTIVE_SOURCE_ID, "default")
                    .putBoolean(KEY_MIGRATED, true)
                    .apply()
            }
        }
}
