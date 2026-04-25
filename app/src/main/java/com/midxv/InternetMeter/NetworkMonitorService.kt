package com.midxv.InternetMeter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class NetworkMonitorService : Service() {

    private val channelId = "InternetMeterChannel"
    private val notificationId = 1
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var floatingTextView: TextView
    private var isOverlayAdded = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateSpeed()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        lastTime = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, buildNotification("Monitoring..."))
        val mode = getSharedPreferences("InternetMeterPrefs", Context.MODE_PRIVATE).getString("monitor_mode", "overlay")
        if (mode == "overlay" && Settings.canDrawOverlays(this)) {
            setupFloatingWindow()
        }
        handler.post(updateRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        removeFloatingWindow()
        super.onDestroy()
    }

    private fun setupFloatingWindow() {
        if (isOverlayAdded) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingTextView = TextView(this)
        floatingTextView.setTextColor(Color.WHITE)
        floatingTextView.textSize = 10f
        floatingTextView.setPadding(10, 0, 10, 0)
        floatingTextView.setBackgroundColor(Color.TRANSPARENT)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val cutout = windowMetrics.windowInsets.displayCutout
            if (cutout != null && cutout.boundingRects.isNotEmpty()) {
                val notchRect = cutout.boundingRects[0]
                layoutParams.x = notchRect.left - 120
                layoutParams.y = notchRect.top + 5
            } else {
                layoutParams.x = 20
                layoutParams.y = 10
            }
        } else {
            layoutParams.x = 20
            layoutParams.y = 10
        }

        try {
            windowManager.addView(floatingTextView, layoutParams)
            isOverlayAdded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingWindow() {
        if (isOverlayAdded && ::windowManager.isInitialized && ::floatingTextView.isInitialized) {
            try {
                windowManager.removeView(floatingTextView)
                isOverlayAdded = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateSpeed() {
        val currentRxBytes = TrafficStats.getTotalRxBytes()
        val currentTxBytes = TrafficStats.getTotalTxBytes()
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastTime

        if (timeDiff > 0) {
            val totalDiff = (currentRxBytes - lastRxBytes) + (currentTxBytes - lastTxBytes)
            val bytesPerSecond = (totalDiff * 1000) / timeDiff
            val speedText = formatSpeed(bytesPerSecond)
            val mode = getSharedPreferences("InternetMeterPrefs", Context.MODE_PRIVATE).getString("monitor_mode", "overlay")

            if (mode == "overlay") {
                if (!isOverlayAdded && Settings.canDrawOverlays(this)) setupFloatingWindow()
                if (isOverlayAdded) floatingTextView.text = speedText
            } else {
                removeFloatingWindow()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(notificationId, buildNotification(speedText))
            }
        }

        lastRxBytes = currentRxBytes
        lastTxBytes = currentTxBytes
        lastTime = currentTime
    }

    private fun formatSpeed(bytes: Long): String {
        if (bytes < 1024) return "$bytes B/s"
        val kb = bytes / 1024f
        if (kb < 1024) return String.format("%.1f K/s", kb)
        return String.format("%.1f M/s", kb / 1024f)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("InternetMeter")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Network Monitor", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}