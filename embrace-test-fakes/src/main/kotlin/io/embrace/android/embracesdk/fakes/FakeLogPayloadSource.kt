package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.LogPayload

class FakeLogPayloadSource : LogPayloadSource {
    var singleLogPayloadsSource: List<LogRequest<LogPayload>> =
        listOf(LogRequest(LogPayload(logs = listOf(sendImmediatelyLog))))

    var batchedLogPayloadSource: LogPayload = LogPayload(logs = listOf(testLog))

    override fun getBatchedLogPayload(): LogPayload = batchedLogPayloadSource

    override fun getSingleLogPayloads(): List<LogRequest<LogPayload>> = singleLogPayloadsSource
}
