package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.logs.LogService

internal class FakeLogService : LogService {

    val logs = mutableListOf<String>()
    val exceptions = mutableListOf<String>()
    val flutterExceptions = mutableListOf<String>()

    override fun log(message: String, severity: Severity, properties: Map<String, Any>?) {
        logs.add(message)
    }

    override fun logException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTrace: String?,
        framework: Embrace.AppFramework,
        exceptionName: String?,
        exceptionMessage: String?
    ) {
        exceptions.add(message)
    }

    override fun logFlutterException(
        message: String,
        severity: Severity,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTrace: String?,
        exceptionName: String?,
        exceptionMessage: String?,
        context: String?,
        library: String?,
    ) {
        flutterExceptions.add(message)
    }

    override fun findInfoLogIds(startTime: Long, endTime: Long): List<String> {
        TODO("Not yet implemented")
    }

    override fun findWarningLogIds(startTime: Long, endTime: Long): List<String> {
        TODO("Not yet implemented")
    }

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        TODO("Not yet implemented")
    }

    override fun getInfoLogsAttemptedToSend(): Int {
        TODO("Not yet implemented")
    }

    override fun getWarnLogsAttemptedToSend(): Int {
        TODO("Not yet implemented")
    }

    override fun getErrorLogsAttemptedToSend(): Int {
        TODO("Not yet implemented")
    }

    override fun getUnhandledExceptionsSent(): Int {
        TODO("Not yet implemented")
    }

    override fun cleanCollections() {
        TODO("Not yet implemented")
    }
}
