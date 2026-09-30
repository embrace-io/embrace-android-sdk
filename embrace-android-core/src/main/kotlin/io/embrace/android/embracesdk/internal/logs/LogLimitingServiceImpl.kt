package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.config.ConfigService

class LogLimitingServiceImpl(
    configService: ConfigService,
) : LogLimitingService {
    private val cfg = configService.config.log
    private val logCounters = mapOf(
        LogSeverity.INFO to LogCounter(cfg::infoLimit),
        LogSeverity.WARNING to LogCounter(cfg::warnLimit),
        LogSeverity.ERROR to LogCounter(cfg::errorLimit),
    )

    override fun getCount(logSeverity: LogSeverity): Int = logCounters.getValue(logSeverity).getCount()

    override fun addIfAllowed(logSeverity: LogSeverity): Boolean = logCounters.getValue(logSeverity).addIfAllowed()

    override fun onPostSessionChange() {
        logCounters.forEach { it.value.clear() }
    }
}
