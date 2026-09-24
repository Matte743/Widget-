package com.matte743.nothingqs

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.text.format.DateFormat
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import java.util.Calendar

/**
 * Glyph Toy that shows the time on the Glyph Matrix: hours on top, minutes below.
 *
 * The system binds this service when the user picks the toy with the Glyph
 * button, and sends [GlyphToy.EVENT_AOD] once a minute when it is the
 * Always-on Glyph Toy. While bound, the clock also redraws on every minute
 * tick and when the time, time zone or 12/24-hour setting changes
 * (Android reports the last one as a time change).
 *
 * It runs in its own process (see the manifest) because GlyphMatrixManager is
 * a per-process singleton, and the Glyph torch uses it at the same time.
 */
class GlyphClockToy : Service() {

    private var manager: GlyphMatrixManager? = null
    private var connected = false
    private var layout = GlyphClockFace.PHONE_3
    private var receiverRegistered = false

    private val toyHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) {
                super.handleMessage(msg)
                return
            }
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_AOD -> draw()
            }
        }
    }
    private val messenger = Messenger(toyHandler)

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = draw()
    }

    override fun onBind(intent: Intent?): IBinder {
        start()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return false
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun start() {
        if (manager != null) return
        val device = GlyphTorchService.deviceId() ?: return
        layout = GlyphClockFace.layoutFor(matrixSide(device))

        val gm = GlyphMatrixManager.getInstance(applicationContext)
        manager = gm
        gm.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                try {
                    gm.register(device)
                    connected = true
                    draw()
                } catch (_: Throwable) {
                    connected = false
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun stop() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(timeReceiver)
            } catch (_: Throwable) {
            }
            receiverRegistered = false
        }
        toyHandler.removeCallbacksAndMessages(null)
        manager?.let { gm ->
            if (connected) {
                try {
                    gm.turnOff()
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
    }

    private fun draw() {
        val gm = manager ?: return
        if (!connected) return
        val now = Calendar.getInstance()
        var hours = now.get(Calendar.HOUR_OF_DAY)
        if (!DateFormat.is24HourFormat(this)) {
            hours %= 12
            if (hours == 0) hours = 12
        }
        try {
            gm.setMatrixFrame(GlyphClockFace.frame(hours, now.get(Calendar.MINUTE), layout))
        } catch (_: Throwable) {
        }
    }

    private fun matrixSide(device: String): Int {
        val reported = try {
            Common.getDeviceMatrixLength()
        } catch (_: Throwable) {
            0
        }
        return when {
            reported > 0 -> reported
            device == Glyph.DEVICE_25111p -> GlyphClockFace.PHONE_4A_PRO.side
            else -> GlyphClockFace.PHONE_3.side
        }
    }
}
