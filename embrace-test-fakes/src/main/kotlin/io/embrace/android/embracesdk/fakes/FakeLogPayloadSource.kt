package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.LogPayload

class FakeLogPayloadSource : LogPayloadSource {

    var singleLogPayloadsSource: List<LogRequest<LogPayload>> =
        listOf(LogRequest(LogPayload(logs = listOf(sendImmediatelyLog))))
    private val batchedLogPayload: LogPayload = LogPayload()

    override fun getBatchedLogPayload(): LogPayload = batchedLogPayload

    override fun getSingleLogPayloads(): List<LogRequest<LogPayload>> = singleLogPayloadsSource
}
