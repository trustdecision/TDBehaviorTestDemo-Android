package com.TrustDecision.TDBehaviorTestDemo

import android.app.Application

class TDBehaviorDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SdkClient.initializeAndStart(this)
    }
}
