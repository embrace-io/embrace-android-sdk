package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.fixtures.nonbatchableLog
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal class FakeLogPayloadSource : LogPayloadSource {

    var logs: LogPayload = LogPayload()
    var nonbatchedLogs: List<LogPayload> = listOf(LogPayload(logs = listOf(nonbatchableLog)))

    override fun getBatchedLogPayload(): LogPayload = logs

    override fun getNonbatchedLogPayloads(): List<LogPayload> = nonbatchedLogs
}
