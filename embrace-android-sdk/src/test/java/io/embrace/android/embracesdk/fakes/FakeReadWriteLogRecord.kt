package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.data.LogRecordData

internal class FakeReadWriteLogRecord : ReadWriteLogRecord {

    private val logRecordData = FakeLogRecordData()

    override fun <T : Any?> setAttribute(key: AttributeKey<T>, value: T): ReadWriteLogRecord {
        TODO("Not yet implemented")
    }

    override fun toLogRecordData(): LogRecordData {
        return logRecordData
    }
}
