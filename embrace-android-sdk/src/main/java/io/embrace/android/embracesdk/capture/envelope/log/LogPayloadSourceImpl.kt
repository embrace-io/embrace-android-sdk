package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordData
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.toNewPayload

internal class LogPayloadSourceImpl(
    private val logSink: LogSink
) : LogPayloadSource {

    override fun getLogPayload(): LogPayload {
        return LogPayload(
            logs = logSink.completedLogs().map(EmbraceLogRecordData::toNewPayload)
        )
    }
}
