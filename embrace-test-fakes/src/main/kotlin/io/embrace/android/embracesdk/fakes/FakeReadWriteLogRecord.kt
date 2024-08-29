package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.logs.ReadWriteLogRecord
import io.opentelemetry.sdk.logs.data.LogRecordData

public class FakeReadWriteLogRecord : ReadWriteLogRecord {

    public val attributes: MutableMap<String, String> = mutableMapOf()

    private val logRecordData = FakeLogRecordData()

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): ReadWriteLogRecord {
        attributes[key.key] = value.toString()
        return this
    }

    override fun toLogRecordData(): LogRecordData {
        return logRecordData
    }
}
