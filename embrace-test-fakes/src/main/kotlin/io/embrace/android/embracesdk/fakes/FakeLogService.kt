package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.logs.LogService
import io.opentelemetry.api.common.AttributeKey

class FakeLogService : LogService {
    class LogData(
        val message: String,
        val severity: Severity,
        val logExceptionType: LogExceptionType,
        val properties: Map<String, Any>?,
    )

    val logs: MutableList<String> = mutableListOf()
    val loggedMessages: MutableList<LogData> = mutableListOf()
    var errorLogIds: List<String> = listOf()

    override fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        customLogAttrs: Map<AttributeKey<String>, String>,
    ) {
        loggedMessages.add(
            LogData(
                message = message,
                severity = severity,
                logExceptionType = logExceptionType,
                properties = properties,
            )
        )
    }

    override fun getErrorLogsCount(): Int {
        return errorLogIds.count()
    }

    override fun cleanCollections() {}
}
