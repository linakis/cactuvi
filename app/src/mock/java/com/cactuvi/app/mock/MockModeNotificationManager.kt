package com.cactuvi.app.mock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cactuvi.app.R
import com.cactuvi.app.ui.LoadingActivity

/**
 * Manages persistent notification for mock mode. Shows notification when app is in foreground,
 * hides when backgrounded.
 */
object MockModeNotificationManager {

    private const val TAG = "MockModeNotification"
    private const val CHANNEL_ID = "mock_server_channel"
    private const val CHANNEL_NAME = "Mock Server"
    private const val NOTIFICATION_ID = 9999

    private var isInitialized = false
    private var notificationManager: NotificationManager? = null
    private var appContext: Context? = null

    /**
     * Initialize notification manager and register lifecycle observer.
     *
     * @param context Application context
     */
    @JvmStatic
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        appContext = context.applicationContext
        notificationManager =
            appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        createNotificationChannel()
        registerLifecycleObserver()

        isInitialized = true
        Log.i(TAG, "MockModeNotificationManager initialized")
    }

    /** Create notification channel for Android O and above. */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_MIN, // Silent, no sound/vibration
                    )
                    .apply {
                        description = "Development mode indicator"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                    }

            notificationManager?.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    /** Register lifecycle observer to show/hide notification based on app state. */
    private fun registerLifecycleObserver() {
        ProcessLifecycleOwner.get()
            .lifecycle
            .addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        // App moved to foreground
                        Log.d(TAG, "App foregrounded - showing notification")
                        showNotification()
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        // App moved to background
                        Log.d(TAG, "App backgrounded - hiding notification")
                        hideNotification()
                    }
                },
            )
    }

    /** Show the mock mode notification. */
    private fun showNotification() {
        val context = appContext ?: return

        // Create intent to return to app when notification is tapped
        val intent =
            Intent(context, LoadingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // Build notification
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("MOCK MODE")
                .setContentText("Development Server Active")
                .setPriority(NotificationCompat.PRIORITY_MIN) // Silent
                .setOngoing(true) // Non-dismissible
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification shown")
    }

    /** Hide the mock mode notification. */
    private fun hideNotification() {
        notificationManager?.cancel(NOTIFICATION_ID)
        Log.d(TAG, "Notification hidden")
    }

    /** Clean up resources. */
    @JvmStatic
    fun shutdown() {
        hideNotification()
        notificationManager = null
        appContext = null
        isInitialized = false
        Log.i(TAG, "MockModeNotificationManager shutdown")
    }
}
