package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.payload.Log
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.internal.ImmutableSpanContext
import io.opentelemetry.api.internal.ImmutableSpanContext.INVALID
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.logs.data.Body
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.resources.Resource

internal class FakeLogRecordData(
    val log: Log = testLog
) : LogRecordData {

    override fun getResource(): Resource {
        return Resource.builder().build()
    }

    override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo {
        return InstrumentationScopeInfo.create("TestLogRecordData")
    }

    override fun getTimestampEpochNanos(): Long {
        return checkNotNull(log.timeUnixNano)
    }

    override fun getObservedTimestampEpochNanos(): Long {
        return checkNotNull(log.timeUnixNano)
    }

    override fun getSpanContext(): SpanContext {
        return if (log.traceId != null && log.spanId != null) {
            ImmutableSpanContext.create(
                log.traceId,
                log.spanId,
                TraceFlags.getDefault(),
                TraceState.getDefault(),
                false,
                true
            )
        } else {
            INVALID
        }
    }

    override fun getSeverity(): Severity {
        return Severity.INFO
    }

    override fun getSeverityText(): String? {
        return log.severityText
    }

    override fun getBody(): Body {
        return Body.string(checkNotNull(log.body))
    }

    override fun getAttributes(): Attributes {
        val attrBuilder = Attributes.builder()
        log.attributes?.forEach { (key, value) ->
            attrBuilder.put(AttributeKey.stringKey(checkNotNull(key)), checkNotNull(value))
        }
        return attrBuilder.build()
    }

    override fun getTotalAttributeCount(): Int {
        return checkNotNull(log.attributes?.size)
    }
}
