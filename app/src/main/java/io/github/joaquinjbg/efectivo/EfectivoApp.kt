package io.github.joaquinjbg.efectivo

import android.app.Application
import io.github.joaquinjbg.efectivo.util.Perf

class EfectivoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Perf.mark("Application.onCreate: ARRANQUE EN FRIO del proceso")
        Graph.seed(this)
    }
}
