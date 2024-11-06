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

        // add OTel exporters to send data to 3rd party destinations
        Embrace.getInstance().addSpanExporter(LogcatSpanExporter())
        Embrace.getInstance().addLogRecordExporter(LogcatLogRecordExporter())

        // start embrace SDK
        Embrace.getInstance().start(this)
    }
}
