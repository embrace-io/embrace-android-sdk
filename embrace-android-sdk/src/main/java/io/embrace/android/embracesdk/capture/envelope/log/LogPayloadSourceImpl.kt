package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal class LogPayloadSourceImpl(
    private val logSink: LogSink
) : LogPayloadSource {

    override fun getLogPayload(): LogPayload {
        return LogPayload(
            logs = logSink.completedLogs()
        )
    }
}
