package com.example.appwatchclone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

class UsageMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "appwatch_native_channel"
        private const val NOTIFICATION_ID = 2001
        private const val INTERVAL_MS = 5000L // 5 secondes
        private const val LOOKBACK_WINDOW_MS = 15000L // 15 dernières secondes
        private const val MAX_HISTORY_ENTRIES = 100
        private const val PREFS_NAME = "appwatch_prefs"
        private const val KEY_APPS = "apps"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastRecordedPackage: String? = null
    private var lastRecordedTime: Long = 0L

    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkRecentUsageEvents()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(monitorRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun checkRecentUsageEvents() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return

        val endTime = System.currentTimeMillis()
        val startTime = endTime - LOOKBACK_WINDOW_MS

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var detectedPackage: String? = null
        var detectedTimestamp: Long = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue

                // Exclure sa propre application, l'interface système et le système Android
                if (pkg == packageName || pkg == "com.android.systemui" || pkg == "android") {
                    continue
                }

                detectedPackage = pkg
                detectedTimestamp = event.timeStamp
            }
        }

        if (detectedPackage != null) {
            val isDuplicate = detectedPackage == lastRecordedPackage && (detectedTimestamp - lastRecordedTime < 4000)
            if (!isDuplicate) {
                lastRecordedPackage = detectedPackage
                lastRecordedTime = detectedTimestamp
                saveAppToHistory(detectedPackage, detectedTimestamp)
            }
        }
    }

    @Synchronized
    private fun saveAppToHistory(pkg: String, time: Long) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_APPS, "[]") ?: "[]"

        try {
            val currentList = JSONArray(rawJson)
            val updatedList = JSONArray()

            val newEntry = JSONObject().apply {
                put("pkg", pkg)
                put("time", time)
            }

            // Insérer la plus récente au sommet
            updatedList.put(newEntry)

            val maxRemaining = MAX_HISTORY_ENTRIES - 1
            for (i in 0 until currentList.length()) {
                if (i >= maxRemaining) break
                updatedList.put(currentList.getJSONObject(i))
            }

            prefs.edit().putString(KEY_APPS, updatedList.toString()).apply()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Surveillance AppWatch Native",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient la surveillance de l'activité des applications en arrière-plan"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AppWatch Native Actif")
            .setContentText("Surveillance des applications récentes en cours...")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}