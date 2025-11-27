package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource

internal class CrashHandlerDataSource : JvmCrashDataSource {
    val handlers = mutableListOf<CrashTeardownHandler>()

    override fun addCrashTeardownHandler(handler: CrashTeardownHandler) {
        handlers.add(handler)
    }

    override fun logUnhandledJvmThrowable(exception: Throwable) {
    }

    override var telemetryModifier: ((TelemetryAttributes) -> SchemaType)? = null

    override fun onDataCaptureEnabled() {
    }

    override fun onDataCaptureDisabled() {
    }

    override fun resetDataCaptureLimits() {
    }

    override fun <T> captureTelemetry(
        inputValidation: () -> Boolean,
        action: TelemetryDestination.() -> T?,
    ): T? = null
}
