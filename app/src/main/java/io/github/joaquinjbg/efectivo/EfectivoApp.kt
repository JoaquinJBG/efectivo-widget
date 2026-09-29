package io.github.joaquinjbg.efectivo

import android.app.Application

class EfectivoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.seed(this)
    }
}
