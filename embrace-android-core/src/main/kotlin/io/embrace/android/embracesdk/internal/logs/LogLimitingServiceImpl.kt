package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.config.ConfigService

class LogLimitingServiceImpl(
    configService: ConfigService,
) : LogLimitingService {
    private val logCounters = mapOf(
        LogSeverity.INFO to LogCounter(configService.logMessageBehavior::getInfoLogLimit),
        LogSeverity.WARNING to LogCounter(configService.logMessageBehavior::getWarnLogLimit),
        LogSeverity.ERROR to LogCounter(configService.logMessageBehavior::getErrorLogLimit)
    )

    override fun getCount(logSeverity: LogSeverity): Int = logCounters.getValue(logSeverity).getCount()

    override fun addIfAllowed(logSeverity: LogSeverity): Boolean = logCounters.getValue(logSeverity).addIfAllowed()

    override fun onPostSessionChange() {
        logCounters.forEach { it.value.clear() }
    }
}
