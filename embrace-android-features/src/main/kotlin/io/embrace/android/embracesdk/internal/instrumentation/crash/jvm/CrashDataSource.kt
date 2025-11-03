package io.embrace.android.embracesdk.internal.instrumentation.crash.jvm

import io.embrace.android.embracesdk.internal.arch.datasource.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource

interface CrashDataSource : DataSource, CrashService {
    fun addCrashTeardownHandler(handler: Lazy<CrashTeardownHandler?>)
}
