package com.example.basicapp

import android.app.Application
import io.embrace.android.embracesdk.Embrace

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Embrace.addBreadcrumb("Application object created")
    }
}
