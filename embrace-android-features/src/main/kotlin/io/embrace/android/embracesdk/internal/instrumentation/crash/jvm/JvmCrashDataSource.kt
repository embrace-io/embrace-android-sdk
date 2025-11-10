package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler

interface JvmCrashDataSource : DataSource {

    fun addCrashTeardownHandler(handler: CrashTeardownHandler)

    /**
     * Logs an unhandled JVM throwable
     */
    fun logUnhandledJvmThrowable(exception: Throwable)
}
