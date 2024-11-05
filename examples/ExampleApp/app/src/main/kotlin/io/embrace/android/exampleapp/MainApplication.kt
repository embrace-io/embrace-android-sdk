package io.embrace.android.exampleapp

import android.app.Application
import io.embrace.android.embracesdk.Embrace

class MainApplication: Application() {

    companion object {
        init {
            System.loadLibrary("emb-samples")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Embrace.getInstance().start(this)
    }
}
