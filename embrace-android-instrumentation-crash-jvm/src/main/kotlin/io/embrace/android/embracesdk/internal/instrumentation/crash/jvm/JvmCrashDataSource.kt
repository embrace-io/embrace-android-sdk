package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes

interface JvmCrashDataSource : DataSource {

    /**
     * Logs an unhandled JVM throwable
     */
    fun logUnhandledJvmThrowable(exception: Throwable)

    /**
     * Allows modification of attributes and schema type on the crash log, if set
     */
    var telemetryModifier: ((TelemetryAttributes) -> SchemaType)?
}
