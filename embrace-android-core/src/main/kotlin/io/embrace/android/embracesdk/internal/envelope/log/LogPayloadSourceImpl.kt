package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.payload.LogPayload

public class LogPayloadSourceImpl(
    private val logSink: LogSink
) : LogPayloadSource {

    override fun getBatchedLogPayload(): LogPayload {
        return LogPayload(
            logs = logSink.flushLogs()
        )
    }

    override fun getNonbatchedLogPayloads(): List<LogPayload> {
        val nonbatchedLogs = mutableListOf<LogPayload>()
        var log = logSink.pollNonbatchedLog()

        while (log != null) {
            nonbatchedLogs.add(LogPayload(logs = listOf(log)))
            log = if (nonbatchedLogs.size < MAX_PAYLOADS) {
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
