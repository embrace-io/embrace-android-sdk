package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.crash.CrashDataSource
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.payload.JsException

class FakeCrashDataSource : CrashDataSource {
    internal var exception: Throwable? = null
    internal var jsException: JsException? = null

    override fun onDataCaptureEnabled() {
        TODO("Not yet implemented")
    }

    override fun onDataCaptureDisabled() {
        TODO("Not yet implemented")
    }

    override fun captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> Unit,
    ) {
        TODO("Not yet implemented")
    }

    override fun resetDataCaptureLimits() {
        TODO("Not yet implemented")
    }

    override fun addCrashTeardownHandler(handler: Lazy<CrashTeardownHandler?>) {
    }

    override fun handleCrash(exception: Throwable) {
        this.exception = exception
    }

    override fun logUnhandledJsException(exception: JsException) {
        jsException = exception
    }
}
