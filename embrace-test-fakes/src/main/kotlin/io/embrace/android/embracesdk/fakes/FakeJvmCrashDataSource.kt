package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource

class FakeJvmCrashDataSource : JvmCrashDataSource {
    internal var exception: Throwable? = null

    override fun onDataCaptureEnabled() {
    }

    override fun onDataCaptureDisabled() {
    }

    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> T?,
    ): T? = null

    override fun resetDataCaptureLimits() {
    }

    override fun addCrashTeardownHandler(handler: CrashTeardownHandler) {
    }

    override fun logUnhandledJvmThrowable(exception: Throwable) {
        this.exception = exception
    }
}
