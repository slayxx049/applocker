package com.callguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AppMonitorService : Service() {
    private lateinit var windowManager: WindowManager
    private var lockView: android.view.View? = null
    private var lockedPackage: String? = null
    @Volatile private var running = false
    private var monitorThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        running = true
        monitorThread = Thread { monitorApps() }.also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun monitorApps() {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var lastEventTime = System.currentTimeMillis() - 10_000
        while (running) {
            try {
                val events = usage.queryEvents(lastEventTime, System.currentTimeMillis())
                val event = UsageEvents.Event()
                var foregroundPackage: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val foregroundType = if (Build.VERSION.SDK_INT >= 29) {
                        event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                            event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    } else {
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    }
                    if (foregroundType) foregroundPackage = event.packageName
                }
                lastEventTime = System.currentTimeMillis() - 1_000

                val protected = getSharedPreferences(SecurityStore.PREFS, MODE_PRIVATE)
                    .getStringSet(SecurityStore.KEY_PROTECTED, emptySet()) ?: emptySet()
                val candidate = foregroundPackage
                if (candidate != null && candidate in protected && candidate != packageName) {
                    val allowedUntil = getSharedPreferences(SecurityStore.PREFS, MODE_PRIVATE)
                        .getLong("allowed_until_$candidate", 0L)
                    if (System.currentTimeMillis() > allowedUntil && lockedPackage != candidate) {
                        showLock(candidate)
                    }
                }
            } catch (_: Exception) {
                // Keep the service alive; the persistent notification indicates monitoring status.
            }
            SystemClock.sleep(450)
        }
    }

    @Synchronized
    private fun showLock(appPackage: String) {
        if (!Settings.canDrawOverlays(this) || lockView != null) return
        lockedPackage = appPackage

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            setBackgroundColor(Color.rgb(246, 248, 252))
        }
        root.addView(TextView(this).apply {
            text = "App locked"
            textSize = 28f
            setTextColor(Color.rgb(23, 32, 51))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Enter your CallGuard PIN to continue."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 20)
        })
        val pin = EditText(this).apply {
            hint = "PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            gravity = Gravity.CENTER
        }
        root.addView(pin, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "Unlock"
            isAllCaps = false
            setOnClickListener {
                val entered = pin.text.toString()
                if (SecurityStore.verifyPin(this@AppMonitorService, entered)) {
                    getSharedPreferences(SecurityStore.PREFS, MODE_PRIVATE).edit()
                        .putLong("allowed_until_$appPackage", System.currentTimeMillis() + 60_000L)
                        .apply()
                    removeLock()
                } else {
                    pin.text.clear()
                    Toast.makeText(this@AppMonitorService, "Incorrect PIN.", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(Button(this).apply {
            text = "Use fingerprint"
            isAllCaps = false
            setOnClickListener {
                // Remove the overlay first so the system biometric prompt can be used.
                removeLock()
                val auth = Intent(this@AppMonitorService, AuthActivity::class.java)
                    .putExtra(AuthActivity.EXTRA_PACKAGE, appPackage)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(auth)
            }
        }, LinearLayout.LayoutParams(-1, -2))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            windowManager.addView(root, params)
            lockView = root
        } catch (_: Exception) {
            lockedPackage = null
        }
    }

    @Synchronized
    fun removeLock() {
        lockView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        lockView = null
        lockedPackage = null
    }

    override fun onDestroy() {
        running = false
        monitorThread?.interrupt()
        removeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "App protection", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("CallGuard is active")
                .setContentText("Monitoring the apps you selected")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("CallGuard is active")
                .setContentText("Monitoring the apps you selected")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(openApp)
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val CHANNEL_ID = "callguard_protection"
        private const val NOTIFICATION_ID = 7101
    }
}
