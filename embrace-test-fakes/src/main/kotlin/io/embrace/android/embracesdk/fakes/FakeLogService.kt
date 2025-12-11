package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.attachments.Attachment

class FakeLogService : LogService {
    class LogData(
        val message: String,
        val severity: Severity,
        val logExceptionType: LogExceptionType,
        val embraceAttributes: Map<String, Any>,
    )

    val logs: MutableList<String> = mutableListOf()
    val loggedMessages: MutableList<LogData> = mutableListOf()
    var errorLogIds: List<String> = listOf()

    override fun log(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        attributes: Map<String, Any>,
        embraceAttributes: Map<String, String>,
        logAttachment: Attachment.EmbraceHosted?,
    ) {
        loggedMessages.add(
            LogData(
                message = message,
                severity = severity,
                logExceptionType = logExceptionType,
                embraceAttributes = attributes,
            )
        )
    }

    override fun getErrorLogsCount(): Int {
        return errorLogIds.count()
    }

    override fun onPostSessionChange() {}
}
