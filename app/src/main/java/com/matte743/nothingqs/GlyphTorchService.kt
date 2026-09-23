package com.matte743.nothingqs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * "Glyph torch": lights every LED of the Glyph Matrix at full brightness.
 * The matrix stays lit only while the app keeps its connection to Nothing's
 * Glyph service, so this runs as a foreground service with a notification.
 */
class GlyphTorchService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.matte743.nothingqs.glyph.TOGGLE"
        const val ACTION_OFF = "com.matte743.nothingqs.glyph.OFF"
        private const val CHANNEL_ID = "glyph_torch"
        private const val NOTIFICATION_ID = 42
        private const val MAX_BRIGHTNESS = 4095
        private const val KEEP_ALIVE_MS = 15_000L

        @Volatile
        var isOn: Boolean = false
            private set

        /** Glyph Matrix device id for this phone, or null if it has none. */
        fun deviceId(): String? = try {
            when {
                Common.is23112() -> Glyph.DEVICE_23112
                Common.is25111p() -> Glyph.DEVICE_25111p
                else -> null
            }
        } catch (_: Throwable) {
            null
        }

        fun isSupported(): Boolean = deviceId() != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var manager: GlyphMatrixManager? = null
    private var connected = false

    private val keepAlive = object : Runnable {
        override fun run() {
            if (isOn && connected) {
                pushFrame()
                handler.postDelayed(this, KEEP_ALIVE_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always enter the foreground first: the service was started with
        // startForegroundService and must call startForeground promptly.
        startInForeground()
        when (intent?.action) {
            ACTION_OFF -> turnOff()
            else -> if (isOn) turnOff() else turnOn()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.glyph_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val offIntent = PendingIntent.getService(
            this, 1,
            Intent(this, GlyphTorchService::class.java).setAction(ACTION_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_glyph)
            .setContentTitle(getString(R.string.glyph_notification_title))
            .setContentText(getString(R.string.glyph_notification_text))
            .setOngoing(true)
            .setColor(getColor(R.color.nt_red))
            .addAction(
                Notification.Action.Builder(null, getString(R.string.glyph_turn_off), offIntent).build()
            )
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun turnOn() {
        val device = deviceId()
        if (device == null) {
            fail()
            return
        }
        Haptics.perform(this, Haptics.Kind.ON)
        isOn = true
        refreshWidget()
        val gm = GlyphMatrixManager.getInstance(applicationContext)
        manager = gm
        gm.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                try {
                    gm.register(device)
                    connected = true
                    try {
                        gm.setGlyphMatrixTimeout(false)
                    } catch (_: Throwable) {
                    }
                    pushFrame()
                    handler.removeCallbacks(keepAlive)
                    handler.postDelayed(keepAlive, KEEP_ALIVE_MS)
                } catch (_: Throwable) {
                    fail()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
                if (isOn) {
                    isOn = false
                    stopEverything()
                }
            }
        })
    }

    private fun pushFrame() {
        val gm = manager ?: return
        val reported = try {
            Common.getDeviceMatrixLength()
        } catch (_: Throwable) {
            0
        }
        val side = if (reported > 0) reported else 25
        try {
            gm.setAppMatrixFrame(IntArray(side * side) { MAX_BRIGHTNESS })
        } catch (_: Throwable) {
            fail()
        }
    }

    private fun turnOff() {
        if (isOn) Haptics.perform(this, Haptics.Kind.OFF)
        isOn = false
        stopEverything()
    }

    private fun fail() {
        isOn = false
        handler.post {
            Toast.makeText(applicationContext, R.string.glyph_failed, Toast.LENGTH_LONG).show()
        }
        stopEverything()
    }

    private fun stopEverything() {
        handler.removeCallbacks(keepAlive)
        manager?.let { gm ->
            if (connected) {
                try {
                    gm.closeAppMatrix()
                } catch (_: Throwable) {
                }
            }
            try {
                gm.unInit()
            } catch (_: Throwable) {
            }
        }
        manager = null
        connected = false
        refreshWidget()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (isOn) {
            isOn = false
            stopEverything()
        }
        super.onDestroy()
    }

    private fun refreshWidget() {
        val app: Context = applicationContext
        Thread { WidgetRenderer.updateAll(app) }.start()
    }
}
