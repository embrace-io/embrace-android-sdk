package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaReadWriteLogRecord

class FakeReadWriteLogRecord : OtelJavaReadWriteLogRecord {

    val attributes: MutableMap<String, String> = mutableMapOf()

    private val logRecordData = FakeLogRecordData()

    override fun <T : Any> setAttribute(key: OtelJavaAttributeKey<T>, value: T): OtelJavaReadWriteLogRecord {
        attributes[key.key] = value.toString()
        return this
    }

    override fun toLogRecordData(): OtelJavaLogRecordData {
        return logRecordData
    }
}
