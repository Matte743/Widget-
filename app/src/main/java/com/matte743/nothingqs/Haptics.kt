package com.matte743.nothingqs

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Crisp, short haptics in the spirit of Nothing OS.
 *
 * Widget taps are handled while the app is in the background. Android drops
 * background vibrations with the "touch" usage, so the "hardware feedback"
 * usage is used instead: it is meant for feedback to a physical interaction
 * and is allowed from the background.
 */
object Haptics {
    enum class Kind { ON, OFF, TICK, CLICK }

    fun perform(context: Context, kind: Kind) {
        if (!Prefs.haptics(context)) return
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return

        val primitive = when (kind) {
            Kind.TICK -> VibrationEffect.Composition.PRIMITIVE_TICK
            else -> VibrationEffect.Composition.PRIMITIVE_CLICK
        }
        val effect = if (vibrator.areAllPrimitivesSupported(primitive, VibrationEffect.Composition.PRIMITIVE_TICK)) {
            val composition = VibrationEffect.startComposition()
            when (kind) {
                // Two quick pulses when something turns on.
                Kind.ON -> composition
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 60)
                // One softer pulse when it turns off.
                Kind.OFF -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.55f)
                Kind.TICK -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f)
                Kind.CLICK -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f)
            }
            composition.compose()
        } else {
            VibrationEffect.createPredefined(
                when (kind) {
                    Kind.TICK -> VibrationEffect.EFFECT_TICK
                    Kind.ON -> VibrationEffect.EFFECT_DOUBLE_CLICK
                    else -> VibrationEffect.EFFECT_CLICK
                }
            )
        }
        val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
        try {
            vibrator.vibrate(effect, attributes)
        } catch (_: RuntimeException) {
            // Never let haptics break a toggle.
        }
    }
}
