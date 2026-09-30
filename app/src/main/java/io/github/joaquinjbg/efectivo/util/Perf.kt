package io.github.joaquinjbg.efectivo.util

import android.os.SystemClock
import android.util.Log

/**
 * Cronómetro del ciclo del widget: toque -> escritura -> repintado.
 *
 * Callado salvo que se active a mano:
 *     adb shell setprop log.tag.EfectivoPerf DEBUG
 */
object Perf {

    const val TAG = "EfectivoPerf"

    val enabled: Boolean get() = Log.isLoggable(TAG, Log.DEBUG)

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Registra cuánto ha pasado desde [startedAt]. */
    fun step(label: String, startedAt: Long) {
        if (enabled) Log.d(TAG, "$label +${now() - startedAt} ms")
    }

    fun mark(label: String) {
        if (enabled) Log.d(TAG, label)
    }
}
