package com.cactuvi.app.utils

import android.content.Context
import com.cactuvi.app.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Mock flavor override of CredentialsManager.
 *
 * Returns localhost:8080 for server URL so that stream URLs are built pointing to
 * MockServerManager, which will proxy stream requests to the real server.
 *
 * API credentials (username/password) are still read from the database to match mock responses.
 */
@Singleton
class CredentialsManager
@Inject
constructor(@ApplicationContext context: Context, private val database: AppDatabase) {

    /**
     * Returns localhost:8080 for mock server. Stream URLs will be built as:
     * http://localhost:8080/movie/username/password/id.mkv MockServerManager will then proxy these
     * to the real server.
     */
    fun getServer(): String {
        return "http://localhost:8080"
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
                // Return localhost for server, but real credentials for username/password
                Credentials("http://localhost:8080", activeSource.username, activeSource.password)
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
