package com.cactuvi.app.mock

import android.content.Context
import android.util.Log
import com.cactuvi.app.BuildConfig
import com.cactuvi.app.data.db.AppDatabase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * Singleton manager for MockWebServer lifecycle. Handles starting/stopping the mock API server and
 * dispatching requests. Supports selective proxying: API requests are mocked, stream requests are
 * proxied to the real server.
 */
class MockServerManager private constructor() {

    private var mockWebServer: MockWebServer? = null
    private var isStarted = false
    private lateinit var appContext: Context
    private var database: AppDatabase? = null
    private val proxyClient = OkHttpClient()

    companion object {
        private const val TAG = "MockServerManager"
        private const val SERVER_PORT = BuildConfig.MOCK_SERVER_PORT

        @Volatile private var instance: MockServerManager? = null

        @JvmStatic
        fun getInstance(): MockServerManager {
            return instance
                ?: synchronized(this) { instance ?: MockServerManager().also { instance = it } }
        }
    }

    /**
     * Initialize and start the mock web server. Starts the server on a background thread to avoid
     * NetworkOnMainThreadException.
     *
     * @param context Application context
     * @param database Database instance for reading active source URL for stream proxying
     */
    fun start(context: Context, database: AppDatabase) {
        if (isStarted) {
            Log.d(TAG, "MockWebServer already started")
            return
        }

        appContext = context.applicationContext
        this.database = database

        // Log stream proxy configuration
        val realServerUrl =
            runBlocking { database.streamSourceDao().getActive()?.server } ?: "[no active source]"
        Log.i(TAG, "Stream proxy enabled → $realServerUrl")

        // Start on background thread to avoid NetworkOnMainThreadException
        val latch = CountDownLatch(1)
        Thread {
                try {
                    mockWebServer =
                        MockWebServer().apply {
                            dispatcher = ProxyingDispatcher(appContext, database, proxyClient)
                            start(SERVER_PORT)
                        }

                    isStarted = true
                    val url = mockWebServer?.url("")
                    Log.i(TAG, "MockWebServer started successfully at: $url")
                    Log.i(TAG, "Supported actions: ${MockResponseProvider.getSupportedActions()}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MockWebServer", e)
                    isStarted = false
                } finally {
                    latch.countDown()
                }
            }
            .start()

        // Wait for server to start (max 5 seconds)
        try {
            latch.await(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted while waiting for MockWebServer to start")
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
    fun isRunning(): Boolean {
        return isStarted
    }

    /**
     * Custom dispatcher that handles both mock API requests and stream proxying. API requests with
     * action parameter are served from local JSON files. Stream requests (movie, live, series
     * paths) are proxied to real IPTV server.
     */
    private class ProxyingDispatcher(
        private val context: Context,
        private val database: AppDatabase,
        private val httpClient: OkHttpClient,
    ) : Dispatcher() {

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path ?: ""
            val url = request.requestUrl

            // Log the incoming request
            Log.d(TAG, "Received request: ${request.method} $path")

            // Extract action query parameter
            val action = url?.queryParameter("action")

            // Route request based on type
            return if (action != null) {
                // Has ?action= query param → Mock from local JSON
                handleApiRequest(action)
            } else if (
                path.startsWith("/movie/") ||
                    path.startsWith("/live/") ||
                    path.startsWith("/series/")
            ) {
                // Stream path → Proxy to real server
                handleStreamRequest(path)
            } else {
                // Unknown request → 404
                Log.w(TAG, "Unknown request type: $path")
                MockResponse().setResponseCode(404).setBody("{\"error\": \"Not found\"}")
            }
        }

        /** Handle API requests by returning mock JSON responses. */
        private fun handleApiRequest(action: String): MockResponse {
            Log.d(TAG, "Action parameter: $action")

            // Check if action is supported
            if (!MockResponseProvider.isActionSupported(action)) {
                Log.w(TAG, "Unsupported action: $action")
                return MockResponse()
                    .setResponseCode(400)
                    .setBody("{\"error\": \"Unsupported action: $action\"}")
            }

            // Load mock response as streaming Buffer (memory-efficient for large files)
            val responseBuffer = MockResponseProvider.loadMockResponseAsBuffer(context, action)
            val responseSize = responseBuffer.size

            Log.d(TAG, "Returning mock response for action: $action ($responseSize bytes)")

            // Return successful response with Buffer body
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Content-Length", responseSize.toString())
                .setBody(responseBuffer)
        }

        /** Handle stream requests by proxying to the real IPTV server. */
        private fun handleStreamRequest(path: String): MockResponse {
            try {
                // Get real server URL from active source
                val realServerUrl = runBlocking {
                    database.streamSourceDao().getActive()?.server ?: return@runBlocking null
                }

                if (realServerUrl == null) {
                    Log.w(TAG, "No active source found - cannot proxy stream")
                    return MockResponse()
                        .setResponseCode(503)
                        .setBody("{\"error\": \"No active source configured\"}")
                }

                // Build target URL: http://real-server.com/movie/user/pass/12345.mkv
                val targetUrl = "${realServerUrl.trimEnd('/')}$path"
                Log.d(TAG, "Proxying stream request to: $targetUrl")

                // Forward request to real server
                val proxyRequest = Request.Builder().url(targetUrl).get().build()

                val proxyResponse = httpClient.newCall(proxyRequest).execute()

                // Copy response from real server to mock response
                val responseBody = proxyResponse.body?.bytes() ?: byteArrayOf()
                val contentType = proxyResponse.header("Content-Type") ?: "application/octet-stream"

                Log.d(
                    TAG,
                    "Proxied stream response: ${proxyResponse.code} (${responseBody.size} bytes)"
                )

                return MockResponse()
                    .setResponseCode(proxyResponse.code)
                    .setHeader("Content-Type", contentType)
                    .setHeader("Content-Length", responseBody.size.toString())
                    .setBody(okio.Buffer().write(responseBody))
            } catch (e: Exception) {
                Log.e(TAG, "Error proxying stream request", e)
                return MockResponse()
                    .setResponseCode(502)
                    .setBody("{\"error\": \"Proxy error: ${e.message}\"}")
            }
        }
    }
}
