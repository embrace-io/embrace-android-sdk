package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.logs.LogLimitingService

class FakeLogLimitingService : LogLimitingService {
    override fun getCount(logSeverity: LogSeverity): Int = 0

    override fun addIfAllowed(logSeverity: LogSeverity): Boolean = true

    override fun onPostSessionChange() {}
}
