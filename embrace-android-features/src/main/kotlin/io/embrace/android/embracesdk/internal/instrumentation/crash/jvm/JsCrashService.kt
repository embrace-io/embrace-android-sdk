package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes

interface JsCrashService {

    /**
     * Logs an unhandled JS exception
     */
    fun logUnhandledJsException(
        name: String,
        message: String,
        type: String?,
        stacktrace: String?,
    )

    /**
     * Appends to the telemetry attributes of a crash.
     */
    fun appendCrashTelemetryAttributes(attributes: TelemetryAttributes): SchemaType
}
