package com.planruler.app

import android.app.Application
import com.planruler.app.di.AppGraph

class PlanRulerApp : Application() {
    lateinit var graph: AppGraph
        private set
    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
