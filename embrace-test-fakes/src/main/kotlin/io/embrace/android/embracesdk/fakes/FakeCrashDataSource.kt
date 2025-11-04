package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.CrashDataSource

class FakeCrashDataSource : CrashDataSource {
    internal var exception: Throwable? = null

    override fun onDataCaptureEnabled() {
        TODO("Not yet implemented")
    }

    override fun onDataCaptureDisabled() {
        TODO("Not yet implemented")
    }

    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> T?,
    ): T? = null

    override fun resetDataCaptureLimits() {
        TODO("Not yet implemented")
    }

    override fun addCrashTeardownHandler(handler: CrashTeardownHandler) {
    }

    override fun logUnhandledJvmException(exception: Throwable) {
        this.exception = exception
    }

    override fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    ) {
    }
}
