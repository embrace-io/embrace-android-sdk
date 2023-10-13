package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger.Severity
import java.util.LinkedList

internal class FakeLoggerAction : InternalEmbraceLogger.LoggerAction {

    val msgQueue = LinkedList<LogMessage>()

    override fun log(
        msg: String,
        severity: Severity,
        throwable: Throwable?,
        logStacktrace: Boolean
    ) {
        msgQueue.add(LogMessage(msg, severity, throwable, logStacktrace))
    }

    internal data class LogMessage(
        val msg: String,
        val severity: Severity,
        val throwable: Throwable?,
        val logStacktrace: Boolean
    )
}
