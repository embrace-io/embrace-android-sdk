package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.nonbatchableLog
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.payload.LogPayload

public class FakeLogPayloadSource : LogPayloadSource {

    public var logs: LogPayload = LogPayload()
    public var nonbatchedLogs: List<LogPayload> = listOf(LogPayload(logs = listOf(nonbatchableLog)))

    override fun getBatchedLogPayload(): LogPayload = logs

    override fun getNonbatchedLogPayloads(): List<LogPayload> = nonbatchedLogs
}
