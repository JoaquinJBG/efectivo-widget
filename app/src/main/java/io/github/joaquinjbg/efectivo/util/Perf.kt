package io.github.joaquinjbg.efectivo.util

import android.os.SystemClock
import android.util.Log

/**
 * Timings for the widget's tap -> write -> redraw cycle.
 *
 * Silent unless someone turns it on:
 *     adb shell setprop log.tag.EfectivoPerf DEBUG
 */
object Perf {

    const val TAG = "EfectivoPerf"

    val enabled: Boolean get() = Log.isLoggable(TAG, Log.DEBUG)

    fun now(): Long = SystemClock.elapsedRealtime()

    /** Logs how long it has been since [startedAt]. */
    fun step(label: String, startedAt: Long) {
        if (enabled) Log.d(TAG, "$label +${now() - startedAt} ms")
    }

    fun mark(label: String) {
        if (enabled) Log.d(TAG, label)
    }
}
