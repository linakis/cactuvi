package com.cactuvi.app.mock

import android.content.Context
import android.util.Log
import com.cactuvi.app.BuildConfig
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Singleton manager for MockWebServer lifecycle. Handles starting/stopping the mock API server and
 * dispatching requests.
 */
class MockServerManager private constructor() {

    private var mockWebServer: MockWebServer? = null
    private var isStarted = false
    private lateinit var appContext: Context

    companion object {
        private const val TAG = "MockServerManager"
        private const val SERVER_PORT = BuildConfig.MOCK_SERVER_PORT

        @Volatile private var instance: MockServerManager? = null

        fun getInstance(): MockServerManager {
            return instance
                ?: synchronized(this) { instance ?: MockServerManager().also { instance = it } }
        }
    }

    /**
     * Initialize and start the mock web server.
     *
     * @param context Application context
     */
    fun start(context: Context) {
        if (isStarted) {
            Log.d(TAG, "MockWebServer already started")
            return
        }

        appContext = context.applicationContext

        try {
            mockWebServer =
                MockWebServer().apply {
                    dispatcher = MockApiDispatcher(appContext)
                    start(SERVER_PORT)
                }

            isStarted = true
            val url = mockWebServer?.url("")
            Log.i(TAG, "MockWebServer started successfully at: $url")
            Log.i(TAG, "Supported actions: ${MockResponseProvider.getSupportedActions()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MockWebServer", e)
            isStarted = false
        }
    }

    /** Stop and shutdown the mock web server. */
    fun shutdown() {
        if (!isStarted) {
            return
        }

        try {
            mockWebServer?.shutdown()
            mockWebServer = null
            isStarted = false
            Log.i(TAG, "MockWebServer stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down MockWebServer", e)
        }
    }

    /** Get the base URL of the running mock server. */
    fun getBaseUrl(): String {
        return if (isStarted) {
            mockWebServer?.url("")?.toString() ?: "http://localhost:$SERVER_PORT/"
        } else {
            "http://localhost:$SERVER_PORT/"
        }
    }

    /** Check if server is currently running. */
    fun isRunning(): Boolean = isStarted

    /** Custom dispatcher to handle mock API requests. */
    private class MockApiDispatcher(private val context: Context) : Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: ""
            val url = request.requestUrl

            // Log the incoming request
            Log.d(TAG, "Received request: ${request.method} $path")

            // Extract action query parameter
            val action = url?.queryParameter("action")

            Log.d(TAG, "Action parameter: $action")

            // Check if action is supported
            if (!MockResponseProvider.isActionSupported(action)) {
                Log.w(TAG, "Unsupported action: $action")
                return MockResponse()
                    .setResponseCode(400)
                    .setBody("{\"error\": \"Unsupported action: $action\"}")
            }

            // Load mock response from assets
            val responseBody = MockResponseProvider.loadMockResponse(context, action)

            // Return successful response
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
                .also {
                    Log.d(
                        TAG,
                        "Returning mock response for action: $action (${responseBody.length} bytes)"
                    )
                }
        }
    }
}
