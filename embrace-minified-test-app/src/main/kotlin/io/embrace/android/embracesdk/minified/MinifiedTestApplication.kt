package io.embrace.android.embracesdk.minified

import android.app.Application
import io.embrace.android.embracesdk.Embrace

class MinifiedTestApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Embrace.start(this) {
            otel {
                addSpanExporter(RecordingSpanExporter)
            }
        }
        Embrace.startSpan(STARTUP_SPAN_NAME).stop()
    }

    companion object {
        const val STARTUP_SPAN_NAME: String = "minified-test-span"
    }
}
