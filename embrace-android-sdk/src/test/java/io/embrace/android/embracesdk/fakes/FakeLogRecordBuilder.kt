package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class FakeLogRecordBuilder : LogRecordBuilder {

    var timestampEpochNanos: Long = 0
    var observedTimestampEpochNanos: Long = 0
    var context: Context? = null
    var severity = Severity.UNDEFINED_SEVERITY_NUMBER
    var severityText: String? = null
    var body: String? = null
    var attributes: MutableMap<String, String> = mutableMapOf()
    var emitCalled: Int = 0

    override fun setTimestamp(timestamp: Long, unit: TimeUnit): LogRecordBuilder {
        timestampEpochNanos = unit.toNanos(timestamp)
        return this
    }

    override fun setTimestamp(instant: Instant): LogRecordBuilder {
        timestampEpochNanos = instant.toEpochMilli() * 1_000_000
        return this
    }

    override fun setObservedTimestamp(timestamp: Long, unit: TimeUnit): LogRecordBuilder {
        observedTimestampEpochNanos = unit.toNanos(timestamp)
        return this
    }

    override fun setObservedTimestamp(instant: Instant): LogRecordBuilder {
        observedTimestampEpochNanos = instant.toEpochMilli() * 1_000_000
        return this
    }

    override fun setContext(context: Context): LogRecordBuilder {
        this.context = context
        return this
    }

    override fun setSeverity(severity: Severity): LogRecordBuilder {
        this.severity = severity
        return this
    }

    override fun setSeverityText(severityText: String): LogRecordBuilder {
        this.severityText = severityText
        return this
    }

    override fun setBody(body: String): LogRecordBuilder {
        this.body = body
        return this
    }

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): LogRecordBuilder {
        attributes[key.key.toString()] = value.toString()
        return this
    }

    override fun emit() {
        emitCalled++
    }
}
