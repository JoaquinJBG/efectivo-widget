package io.github.joaquinjbg.efectivo.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short confirmation buzz after a write.
 *
 * A widget callback runs with the process in the background, and Android drops vibrations
 * that arrive without a usage the system recognises as touch feedback — so the attributes
 * below are load-bearing, not decoration.
 */
object Haptics {

    fun tick(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) {
            Perf.mark("Haptics: el dispositivo no tiene vibrador")
            return
        }

        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The stock click haptic: crisper than a raw duration on a linear motor.
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        Perf.mark("Haptics: vibracion enviada")
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
