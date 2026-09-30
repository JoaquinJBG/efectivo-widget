package io.github.joaquinjbg.efectivo.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibración corta de confirmación después de escribir.
 *
 * El callback de un widget corre con el proceso en segundo plano, y Android descarta las
 * vibraciones que llegan sin un "usage" que reconozca como respuesta táctil: los atributos
 * de abajo no son decoración, sin ellos no vibra.
 */
object Haptics {

    fun tick(context: Context) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) {
            Perf.mark("Haptics: el dispositivo no tiene vibrador")
            return
        }

        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // El clic estándar: más seco que una duración cruda en un motor lineal.
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
