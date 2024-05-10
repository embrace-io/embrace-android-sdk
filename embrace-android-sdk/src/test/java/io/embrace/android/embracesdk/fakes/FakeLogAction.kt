package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.logging.EmbLoggerImpl.Severity
import java.util.LinkedList

internal class FakeLogAction : EmbLoggerImpl.LogAction {

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
