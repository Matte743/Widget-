package com.matte743.nothingqs

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.telephony.TelephonyManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Snapshot of everything the widget shows. */
data class QsState(
    val wifi: Boolean,
    val data: Boolean,
    val bluetooth: Boolean,
    val hotspot: Boolean,
    val torch: Boolean,
    val glyph: Boolean,
    /** Perceived brightness, 0..1, as on the system slider. */
    val brightness: Float,
    val autoBrightness: Boolean,
    val volume: Int,
    val volumeMax: Int,
    val muted: Boolean,
    val canWriteSettings: Boolean,
)

object SystemControls {

    const val SEGMENTS = 10

    fun readState(context: Context, queryTorch: Boolean = true): QsState {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return QsState(
            wifi = isWifiOn(context),
            data = isMobileDataOn(context),
            bluetooth = isBluetoothOn(context),
            hotspot = isHotspotOn(context),
            torch = if (queryTorch) Torch.query(context) ?: Prefs.torchCache(context) else Prefs.torchCache(context),
            glyph = GlyphTorchService.isOn,
            brightness = Brightness.get(context),
            autoBrightness = Brightness.isAuto(context),
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            volumeMax = max,
            muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
            canWriteSettings = Settings.System.canWrite(context),
        )
    }

    // ---------------------------------------------------------------- Wi-Fi

    fun isWifiOn(context: Context): Boolean = try {
        context.getSystemService(WifiManager::class.java).isWifiEnabled
    } catch (_: RuntimeException) {
        Settings.Global.getInt(context.contentResolver, "wifi_on", 0) != 0
    }

    // ---------------------------------------------------------- Mobile data

    fun isMobileDataOn(context: Context): Boolean = try {
        context.getSystemService(TelephonyManager::class.java).isDataEnabled
    } catch (_: RuntimeException) {
        Settings.Global.getInt(context.contentResolver, "mobile_data", 0) != 0
    }

    // ------------------------------------------------------------ Bluetooth

    fun isBluetoothOn(context: Context): Boolean = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled ?: false
    } catch (_: RuntimeException) {
        Settings.Global.getInt(context.contentResolver, "bluetooth_on", 0) != 0
    }

    fun hasBluetoothPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    // -------------------------------------------------------------- Hotspot

    private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_WIFI_AP_STATE = "wifi_state"
    private const val WIFI_AP_STATE_ENABLING = 12
    private const val WIFI_AP_STATE_ENABLED = 13

    fun isHotspotOn(context: Context): Boolean {
        // The system keeps the last hotspot state as a sticky broadcast.
        try {
            val sticky = context.registerReceiver(
                null, IntentFilter(ACTION_WIFI_AP_STATE_CHANGED), Context.RECEIVER_EXPORTED
            )
            if (sticky != null) {
                val state = sticky.getIntExtra(EXTRA_WIFI_AP_STATE, -1)
                if (state != -1) return state == WIFI_AP_STATE_ENABLED || state == WIFI_AP_STATE_ENABLING
            }
        } catch (_: RuntimeException) {
        }
        return try {
            val wifi = context.getSystemService(WifiManager::class.java)
            wifi.javaClass.getMethod("isWifiApEnabled").invoke(wifi) as Boolean
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------- Volume

    /** Number of lit segments for the current media volume. */
    fun volumeSegments(state: QsState): Int {
        if (state.muted || state.volume == 0) return 0
        return ((state.volume.toFloat() / state.volumeMax) * SEGMENTS).roundToInt().coerceIn(1, SEGMENTS)
    }

    fun setVolumeSegment(context: Context, segment: Int) {
        setVolumeFraction(context, segment.toFloat() / SEGMENTS, minimumOne = true)
    }

    /** Current media volume as a fraction 0..1 (0 when muted). */
    fun volumeFraction(context: Context): Float {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return 0f
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    /** Sets the media volume from a fraction 0..1. Returns the step that was applied. */
    fun setVolumeFraction(context: Context, fraction: Float, minimumOne: Boolean = false): Int {
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val low = if (minimumOne) 1 else 0
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(low, max)
        if (target > 0 && audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != target) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        return target
    }

    fun volumeMax(context: Context): Int =
        context.getSystemService(AudioManager::class.java).getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    fun toggleMute(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
    }
}

/**
 * Screen brightness. The system slider is perceptual (gamma), while the stored
 * setting is linear, so the conversion mirrors Android's BrightnessUtils.
 */
object Brightness {
    private const val R = 0.5f
    private const val A = 0.17883277f
    private const val B = 0.28466892f
    private const val C = 0.55991073f
    private const val MIN = 1
    private const val MAX = 255

    private fun gammaToLinear(gamma: Float): Float {
        val v = gamma.coerceIn(0f, 1f)
        val ret = if (v <= R) (v / R) * (v / R) else exp((v - C) / A) + B
        return (ret / 12f).coerceIn(0f, 1f)
    }

    private fun linearToGamma(linear: Float): Float {
        val v = linear.coerceIn(0f, 1f) * 12f
        val ret = if (v <= 1f) R * sqrt(v) else A * ln(v - B) + C
        return ret.coerceIn(0f, 1f)
    }

    fun get(context: Context): Float {
        val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return linearToGamma((raw - MIN).toFloat() / (MAX - MIN))
    }

    fun isAuto(context: Context): Boolean = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

    fun segments(state: QsState): Int =
        (state.brightness * SystemControls.SEGMENTS).roundToInt().coerceIn(1, SystemControls.SEGMENTS)

    /** Sets a perceived level for [segment] (1..10). Turns adaptive brightness off. */
    fun setSegment(context: Context, segment: Int): Boolean =
        setLevel(context, segment.toFloat() / SystemControls.SEGMENTS)

    /** Sets a perceived level 0..1, like the system slider. Turns adaptive brightness off. */
    fun setLevel(context: Context, gamma: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val raw = (MIN + gammaToLinear(gamma) * (MAX - MIN)).roundToInt().coerceIn(MIN, MAX)
        val resolver = context.contentResolver
        if (isAuto(context)) {
            Settings.System.putInt(
                resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
        }
        if (Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1) == raw) return true
        return Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, raw)
    }

    fun toggleAuto(context: Context): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val next = if (isAuto(context)) Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        else Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        return Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, next)
    }
}

/** Rear camera flashlight. */
object Torch {
    private fun cameraId(manager: CameraManager): String? = try {
        manager.cameraIdList.firstOrNull { id ->
            val c = manager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Android has no getter for the torch state, but a torch callback reports
     * the current state right after registration. Must not run on the main thread.
     */
    fun query(context: Context, timeoutMs: Long = 400): Boolean? {
        val manager = context.getSystemService(CameraManager::class.java) ?: return null
        val id = cameraId(manager) ?: return null
        val thread = HandlerThread("torch-query").apply { start() }
        val latch = CountDownLatch(1)
        var result: Boolean? = null
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id && latch.count > 0) {
                    result = enabled
                    latch.countDown()
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id && latch.count > 0) {
                    result = false
                    latch.countDown()
                }
            }
        }
        return try {
            manager.registerTorchCallback(callback, Handler(thread.looper))
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            result?.also { Prefs.setTorchCache(context, it) }
        } catch (_: Exception) {
            null
        } finally {
            try {
                manager.unregisterTorchCallback(callback)
            } catch (_: Exception) {
            }
            thread.quitSafely()
        }
    }

    fun set(context: Context, on: Boolean): Boolean {
        val manager = context.getSystemService(CameraManager::class.java) ?: return false
        val id = cameraId(manager) ?: return false
        return try {
            manager.setTorchMode(id, on)
            Prefs.setTorchCache(context, on)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/** Intents for the system panels used when a setting cannot be switched directly. */
object SystemPanels {
    fun wifi(): List<Intent> = listOf(
        Intent(Settings.Panel.ACTION_WIFI),
        Intent(Settings.ACTION_WIFI_SETTINGS),
    )

    fun mobileData(): List<Intent> = listOf(
        Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
        Intent(Settings.ACTION_DATA_USAGE_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )

    fun bluetoothEnable(): List<Intent> = listOf(
        Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
    )

    fun bluetoothDisable(): List<Intent> = listOf(
        Intent("android.bluetooth.adapter.action.REQUEST_DISABLE"),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
    )

    fun hotspot(): List<Intent> = listOf(
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$WifiTetherSettingsActivity"),
        Intent().setClassName("com.android.settings", "com.android.settings.Settings\$TetherSettingsActivity"),
        Intent().setClassName("com.android.settings", "com.android.settings.TetherSettings"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )
}
