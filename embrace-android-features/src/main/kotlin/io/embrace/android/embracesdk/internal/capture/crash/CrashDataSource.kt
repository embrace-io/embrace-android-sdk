package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.internal.arch.datasource.DataSource

interface CrashDataSource : DataSource, CrashService {
    fun addCrashTeardownHandler(handler: Lazy<CrashTeardownHandler?>)
}
