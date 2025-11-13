package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes

class FakeJvmCrashDataSource : JvmCrashDataSource {
    var exception: Throwable? = null
    override var telemetryModifier: ((TelemetryAttributes) -> SchemaType)? = null

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
