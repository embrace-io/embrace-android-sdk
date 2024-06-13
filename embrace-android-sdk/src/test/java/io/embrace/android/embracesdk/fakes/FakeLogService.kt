package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.EventType
import io.embrace.android.embracesdk.LogExceptionType
import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.internal.logs.LogService

internal class FakeLogService : LogService {

    val logs = mutableListOf<String>()
    val exceptions = mutableListOf<String>()
    val flutterExceptions = mutableListOf<String>()

    override fun log(
        message: String,
        type: EventType,
        logExceptionType: LogExceptionType,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        framework: Embrace.AppFramework,
        context: String?,
        library: String?,
        exceptionName: String?,
        exceptionMessage: String?
    ) {
        if (type.getSeverity() == null) {
            return
        }

        if (logExceptionType == LogExceptionType.NONE) {
            logs.add(message)
        } else {
            if (framework == Embrace.AppFramework.FLUTTER) {
                flutterExceptions.add(message)
            } else {
                exceptions.add("$exceptionName $exceptionMessage")
            }
        }
    }

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
        exceptions.add("$exceptionName $exceptionMessage $stackTrace")
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

    override fun findErrorLogIds(startTime: Long, endTime: Long): List<String> {
        TODO("Not yet implemented")
    }

    override fun cleanCollections() {
        TODO("Not yet implemented")
    }
}
