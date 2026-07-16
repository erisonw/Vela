package com.vela.app

import android.app.Application
import com.vela.app.di.VelaGraph

class VelaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        VelaGraph.initialize(this)
    }
}
