package com.example.tdbehaviortestdemo_android

import android.app.Application

class TDBehaviorDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SdkClient.initializeAndStart(this)
    }
}
