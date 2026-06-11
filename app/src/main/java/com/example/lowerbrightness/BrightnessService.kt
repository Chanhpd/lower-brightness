package com.example.lowerbrightness

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import android.widget.RemoteViews
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class BrightnessService : Service() {
    companion object {
        val remainingSeconds = MutableStateFlow(0)
        val isTimerActive = MutableStateFlow(false)
    }

    private var overlayView: android.view.View? = null
    private val overlayWindowManager by lazy {
        getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var currentOpacity: Float = 0f
    private var currentColor: FilterColor = FilterColor.BLACK

    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_START" -> {
                currentOpacity = intent.getFloatExtra("OPACITY", 0f)
                val colorName = intent.getStringExtra("COLOR") ?: FilterColor.BLACK.name
                currentColor = FilterColor.valueOf(colorName)
                updateOverlay()
                startForeground(1, createNotification())
            }
            "ACTION_STOP_FILTER" -> {
                currentOpacity = 0f
                updateOverlay()
                if (!isTimerActive.value) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateNotification()
                }
            }
            "ACTION_STOP" -> {
                stopTimer()
                currentOpacity = 0f
                updateOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            "ACTION_INCREASE" -> {
                currentOpacity = (currentOpacity + 0.1f).coerceAtMost(0.9f)
                updateOverlay()
                updateNotification()
            }
            "ACTION_DECREASE" -> {
                currentOpacity = (currentOpacity - 0.1f).coerceAtLeast(0f)
                updateOverlay()
                updateNotification()
            }
            "ACTION_START_TIMER" -> {
                val seconds = intent.getIntExtra("DURATION_SECONDS", 0)
                if (seconds > 0) {
                    startTimer(seconds)
                }
            }
            "ACTION_CANCEL_TIMER" -> {
                stopTimer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer(seconds: Int) {
        isTimerActive.value = true
        remainingSeconds.value = seconds
        
        // Ensure service is running in foreground (showing notification)
        startForeground(1, createNotification())
        
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (remainingSeconds.value > 0) {
                delay(1000)
                remainingSeconds.value -= 1
                updateNotification()
            }
            // Lock screen when timer is finished
            lockScreen()
            stopTimer()
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        isTimerActive.value = false
        remainingSeconds.value = 0
        
        if (currentOpacity > 0f) {
            updateNotification()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun lockScreen() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        if (devicePolicyManager.isAdminActive(componentName)) {
            try {
                devicePolicyManager.lockNow()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "brightness_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Bộ lọc màn hình",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val remoteViews = RemoteViews(packageName, R.layout.notification_brightness)
        remoteViews.setTextViewText(R.id.tvPercent, "${(currentOpacity * 100).toInt()}%")
        remoteViews.setProgressBar(R.id.progressBar, 100, (currentOpacity * 100).toInt(), false)

        if (isTimerActive.value) {
            val minutes = remainingSeconds.value / 60
            val seconds = remainingSeconds.value % 60
            remoteViews.setTextViewText(R.id.tvTimer, String.format("Hẹn giờ tắt màn hình: %02d:%02d", minutes, seconds))
            remoteViews.setViewVisibility(R.id.tvTimer, android.view.View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.tvTimer, android.view.View.GONE)
        }

        val decreaseIntent = Intent(this, BrightnessService::class.java).apply { action = "ACTION_DECREASE" }
        remoteViews.setOnClickPendingIntent(R.id.btnDecrease, PendingIntent.getService(this, 1, decreaseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val increaseIntent = Intent(this, BrightnessService::class.java).apply { action = "ACTION_INCREASE" }
        remoteViews.setOnClickPendingIntent(R.id.btnIncrease, PendingIntent.getService(this, 2, increaseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val stopIntent = Intent(this, BrightnessService::class.java).apply { action = "ACTION_STOP" }
        remoteViews.setOnClickPendingIntent(R.id.btnStop, PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(this, 4, openAppIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setCustomContentView(remoteViews)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(1, createNotification())
    }

    private fun updateOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        overlayView?.let {
            try {
                overlayWindowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (currentOpacity > 0f) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            overlayView = android.view.View(this).apply {
                setBackgroundColor(
                    android.graphics.Color.argb(
                        (currentOpacity * 255).toInt(),
                        currentColor.r,
                        currentColor.g,
                        currentColor.b
                    )
                )
            }

            overlayWindowManager.addView(overlayView, params)
        } else {
            // we don't stop the service here immediately because they might want to increase it again
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayView?.let {
            try {
                overlayWindowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}