package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal class LogPayloadSourceImpl(
    private val logSink: LogSink,
) : LogPayloadSource {

    override fun getBatchedLogPayload(): LogPayload {
        return LogPayload(
            logs = logSink.flushBatch()
        )
    }

    override fun getSingleLogPayloads(): List<LogRequest<LogPayload>> {
        val logRequests = mutableListOf<LogRequest<LogPayload>>()
        var logRequest = logSink.pollUnbatchedLog()

        while (logRequest != null) {
            logRequests.add(
                LogRequest(
                    payload = LogPayload(logs = listOf(logRequest.payload)),
                    defer = logRequest.defer
                )
            )
            logRequest = if (logRequests.size < MAX_PAYLOADS) {
                logSink.pollUnbatchedLog()
            } else {
                null
            }
        }

        return logRequests
    }

    private companion object {
        private const val MAX_PAYLOADS = 10
    }
}
