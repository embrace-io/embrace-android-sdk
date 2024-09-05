package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal class LogPayloadSourceImpl(
    private val logSink: LogSink
) : LogPayloadSource {

    override fun getBatchedLogPayload(): LogPayload {
        return LogPayload(
            logs = logSink.flushLogs()
        )
    }

    override fun getNonbatchedLogPayloads(): List<LogRequest<LogPayload>> {
        val nonbatchedLogs = mutableListOf<LogRequest<LogPayload>>()
        var logRequest = logSink.pollNonbatchedLog()

        while (logRequest != null) {
            nonbatchedLogs.add(
                LogRequest(
                    payload = LogPayload(logs = listOf(logRequest.payload)),
                    defer = logRequest.defer
                )
            )
            logRequest = if (nonbatchedLogs.size < MAX_PAYLOADS) {
                logSink.pollNonbatchedLog()
            } else {
                null
            }
        }

        return nonbatchedLogs
    }

    private companion object {
        private const val MAX_PAYLOADS = 10
    }
}
