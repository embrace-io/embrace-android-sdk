package io.embrace.android.embracesdk.internal.capture.crash

import io.embrace.android.embracesdk.internal.arch.datasource.LogDataSource

interface CrashDataSource : LogDataSource, CrashService {
    fun addCrashTeardownHandler(handler: Lazy<CrashTeardownHandler>)
}
