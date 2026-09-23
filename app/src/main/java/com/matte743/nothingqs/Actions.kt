package com.matte743.nothingqs

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Every widget action, and how each one is carried out. */
object Actions {
    private const val PREFIX = "com.matte743.nothingqs.action."
    const val WIFI = PREFIX + "WIFI"
    const val DATA = PREFIX + "DATA"
    const val BLUETOOTH = PREFIX + "BLUETOOTH"
    const val HOTSPOT = PREFIX + "HOTSPOT"
    const val TORCH = PREFIX + "TORCH"
    const val GLYPH = PREFIX + "GLYPH"
    const val BRIGHTNESS_SET = PREFIX + "BRIGHTNESS_SET"
    const val BRIGHTNESS_AUTO = PREFIX + "BRIGHTNESS_AUTO"
    const val VOLUME_SET = PREFIX + "VOLUME_SET"
    const val VOLUME_MUTE = PREFIX + "VOLUME_MUTE"
    const val OPEN_APP = PREFIX + "OPEN_APP"

    const val EXTRA_LEVEL = "level"

    private fun requestCode(action: String, level: Int) = action.hashCode() * 31 + level

    private fun baseIntent(context: Context, target: Class<*>, action: String, level: Int) =
        Intent(context, target)
            .setAction(action)
            .putExtra(EXTRA_LEVEL, level)
            // A distinct data URI keeps one PendingIntent per action and level.
            .setData(Uri.parse("qs://${action.substringAfterLast('.')}/$level"))

    /** Handled silently in the background by [ActionReceiver]. */
    fun broadcast(context: Context, action: String, level: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(action, level),
            baseIntent(context, ActionReceiver::class.java, action, level)
                // Foreground priority: widget taps must feel instant.
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Opens the draggable slider panel; [level] > 0 also applies that level at once. */
    fun slider(context: Context, target: Int, level: Int = 0): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode("SLIDER$target", level),
            Intent(context, SliderActivity::class.java)
                .putExtra(SliderActivity.EXTRA_TARGET, target)
                .putExtra(EXTRA_LEVEL, level)
                .setData(Uri.parse("qs://slider/$target/$level"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Goes through the invisible [TrampolineActivity], needed to open system panels. */
    fun activity(context: Context, action: String, level: Int = 0): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode(action, level),
            baseIntent(context, TrampolineActivity::class.java, action, level)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The Glyph torch tile starts its foreground service directly. */
    fun glyphService(context: Context): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            requestCode(GLYPH, 0),
            Intent(context, GlyphTorchService::class.java).setAction(GlyphTorchService.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * Background work for a widget tap. Runs on a worker thread.
     * Returns false if the action needs an activity instead.
     */
    fun handleInBackground(context: Context, action: String, level: Int): Boolean {
        when (action) {
            BRIGHTNESS_SET -> {
                Haptics.perform(context, Haptics.Kind.TICK)
                return Brightness.setSegment(context, level)
            }

            BRIGHTNESS_AUTO -> {
                val wasAuto = Brightness.isAuto(context)
                Haptics.perform(context, if (wasAuto) Haptics.Kind.OFF else Haptics.Kind.ON)
                return Brightness.toggleAuto(context)
            }

            VOLUME_SET -> {
                Haptics.perform(context, Haptics.Kind.TICK)
                SystemControls.setVolumeSegment(context, level)
            }

            VOLUME_MUTE -> {
                Haptics.perform(context, Haptics.Kind.CLICK)
                SystemControls.toggleMute(context)
            }

            TORCH -> {
                val on = Torch.query(context) ?: Prefs.torchCache(context)
                Haptics.perform(context, if (on) Haptics.Kind.OFF else Haptics.Kind.ON)
                Torch.set(context, !on)
            }

            WIFI -> {
                val on = SystemControls.isWifiOn(context)
                Haptics.perform(context, if (on) Haptics.Kind.OFF else Haptics.Kind.ON)
                return ShizukuShell.awaitReady() && ShizukuShell.setWifi(!on)
            }

            DATA -> {
                val on = SystemControls.isMobileDataOn(context)
                Haptics.perform(context, if (on) Haptics.Kind.OFF else Haptics.Kind.ON)
                return ShizukuShell.awaitReady() && ShizukuShell.setMobileData(!on)
            }

            BLUETOOTH -> {
                val on = SystemControls.isBluetoothOn(context)
                Haptics.perform(context, if (on) Haptics.Kind.OFF else Haptics.Kind.ON)
                return ShizukuShell.awaitReady() && ShizukuShell.setBluetooth(!on)
            }

            else -> return false
        }
        return true
    }

    /** Foreground work for a widget tap, from [TrampolineActivity]. */
    fun handleInActivity(activity: Activity, action: String, level: Int) {
        when (action) {
            WIFI -> {
                Haptics.perform(activity, Haptics.Kind.CLICK)
                if (!tryShizukuFromActivity(activity, action, level)) startFirst(activity, SystemPanels.wifi())
            }

            DATA -> {
                Haptics.perform(activity, Haptics.Kind.CLICK)
                if (!tryShizukuFromActivity(activity, action, level)) startFirst(activity, SystemPanels.mobileData())
            }

            BLUETOOTH -> {
                Haptics.perform(activity, Haptics.Kind.CLICK)
                if (tryShizukuFromActivity(activity, action, level)) return
                if (!SystemControls.hasBluetoothPermission(activity)) {
                    Toast.makeText(activity, R.string.bt_permission_needed, Toast.LENGTH_LONG).show()
                    openApp(activity)
                } else if (SystemControls.isBluetoothOn(activity)) {
                    startFirst(activity, SystemPanels.bluetoothDisable())
                } else {
                    startFirst(activity, SystemPanels.bluetoothEnable())
                }
            }

            HOTSPOT -> {
                Haptics.perform(activity, Haptics.Kind.CLICK)
                startFirst(activity, SystemPanels.hotspot())
            }

            GLYPH -> {
                activity.startForegroundService(
                    Intent(activity, GlyphTorchService::class.java).setAction(GlyphTorchService.ACTION_TOGGLE)
                )
            }

            else -> {
                Haptics.perform(activity, Haptics.Kind.CLICK)
                openApp(activity)
            }
        }
    }

    /** Shizuku mode was chosen but the widget went through the trampoline: still try it first. */
    private fun tryShizukuFromActivity(activity: Activity, action: String, level: Int): Boolean {
        if (!Prefs.useShizuku(activity) || !ShizukuShell.hasPermission()) return false
        Thread {
            handleInBackground(activity.applicationContext, action, level)
            WidgetRenderer.updateAllDelayed(activity.applicationContext)
        }.start()
        return true
    }

    private fun openApp(activity: Activity) {
        activity.startActivity(
            Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun startFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}
