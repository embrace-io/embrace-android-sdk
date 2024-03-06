package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testLog
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.internal.ImmutableSpanContext
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.logs.data.Body
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.resources.Resource

internal class FakeLogRecordData : LogRecordData {

    override fun getResource(): Resource {
        return Resource.builder().build()
    }

    override fun getInstrumentationScopeInfo(): InstrumentationScopeInfo {
        return InstrumentationScopeInfo.create("TestLogRecordData")
    }

    override fun getTimestampEpochNanos(): Long {
        return testLog.timeUnixNanos
    }

    override fun getObservedTimestampEpochNanos(): Long {
        return testLog.timeUnixNanos
    }

    override fun getSpanContext(): SpanContext {
        return ImmutableSpanContext.create(testLog.traceId, testLog.spanId, TraceFlags.getDefault(), TraceState.getDefault(), false, true)
    }

    override fun getSeverity(): Severity {
        return Severity.INFO
    }

    override fun getSeverityText(): String? {
        return testLog.severityText
    }

    override fun getBody(): Body {
        return Body.string(checkNotNull(testLog.body.message))
    }

    override fun getAttributes(): Attributes {
        val attrBuilder = Attributes.builder()
        testLog.attributes.forEach { (key, value) ->
            attrBuilder.put(AttributeKey.stringKey(key), value as String)
        }
        return attrBuilder.build()
    }

    override fun getTotalAttributeCount(): Int {
        return testLog.attributes.size
    }
}
