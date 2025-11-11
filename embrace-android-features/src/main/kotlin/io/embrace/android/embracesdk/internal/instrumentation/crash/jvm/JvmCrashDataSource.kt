package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler

interface JvmCrashDataSource : DataSource {

    /**
     * Registers a callback that will be invoked after a JVM crash.
     */
    fun addCrashTeardownHandler(handler: CrashTeardownHandler)

    /**
     * Logs an unhandled JVM throwable
     */
    fun logUnhandledJvmThrowable(exception: Throwable)

    /**
     * Allows modification of attributes and schema type on the crash log, if set
     */
    var telemetryModifier: ((TelemetryAttributes) -> SchemaType)?
}
