package com.cactuvi.app.utils

import android.content.Context
import com.cactuvi.app.data.db.AppDatabase
import kotlinx.coroutines.runBlocking

/**
 * Provides access to credentials from the active source in the database. This is a compatibility
 * layer that reads from the database instead of SharedPreferences.
 *
 * Note: Methods use runBlocking for synchronous access. For async operations, use
 * SourceManager.getActiveSource() directly.
 */
class CredentialsManager(context: Context) {

    private val database = AppDatabase.getInstance(context.applicationContext)

    companion object {
        @Volatile private var instance: CredentialsManager? = null

        fun getInstance(context: Context): CredentialsManager {
            return instance
                ?: synchronized(this) {
                    instance
                        ?: CredentialsManager(context.applicationContext).also { instance = it }
                }
        }
    }

    fun getServer(): String {
        return runBlocking { database.streamSourceDao().getActive()?.server ?: "" }
    }

    fun getUsername(): String {
        return runBlocking { database.streamSourceDao().getActive()?.username ?: "" }
    }

    fun getPassword(): String {
        return runBlocking { database.streamSourceDao().getActive()?.password ?: "" }
    }

    fun isConfigured(): Boolean {
        return runBlocking { database.streamSourceDao().getActive() != null }
    }

    data class Credentials(
        val server: String,
        val username: String,
        val password: String,
    )

    fun getCredentials(): Credentials? {
        return runBlocking {
            val activeSource = database.streamSourceDao().getActive()
            if (activeSource != null) {
                Credentials(activeSource.server, activeSource.username, activeSource.password)
            } else {
                null
            }
        }
    }

    // Legacy method - no longer needed, but kept for compatibility
    @Deprecated("Credentials are now stored in database only", ReplaceWith("Use SourceManager"))
    fun saveCredentials(server: String, username: String, password: String) {
        // No-op - credentials are managed through SourceManager
    }

    // Legacy method - no longer needed
    @Deprecated("Credentials are now stored in database only", ReplaceWith("Use SourceManager"))
    fun clear() {
        // No-op - sources are managed through SourceManager
    }
}
