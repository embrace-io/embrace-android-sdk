@file:Suppress("TYPEALIAS_EXPANSION_DEPRECATION")

package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaBody
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaInstrumentationScopeInfo
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaResource
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSeverity
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceFlags
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceState
import io.opentelemetry.api.internal.ImmutableSpanContext
import io.opentelemetry.api.internal.ImmutableSpanContext.INVALID

class FakeLogRecordData(
    val log: Log = testLog,
) : OtelJavaLogRecordData {

    override fun getResource(): OtelJavaResource {
        return OtelJavaResource.builder().build()
    }

    override fun getInstrumentationScopeInfo(): OtelJavaInstrumentationScopeInfo {
        return OtelJavaInstrumentationScopeInfo.create("TestLogRecordData")
    }

    override fun getTimestampEpochNanos(): Long {
        return checkNotNull(log.timeUnixNano)
    }

    override fun getObservedTimestampEpochNanos(): Long {
        return checkNotNull(log.timeUnixNano)
    }

    override fun getSpanContext(): OtelJavaSpanContext {
        val traceId = log.traceId
        val spanId = log.spanId
        return if (traceId != null && spanId != null) {
            ImmutableSpanContext.create(
                traceId,
                spanId,
                OtelJavaTraceFlags.getDefault(),
                OtelJavaTraceState.getDefault(),
                false,
                true
            )
        } else {
            INVALID
        }
    }

    override fun getSeverity(): OtelJavaSeverity {
        return OtelJavaSeverity.INFO
    }

    override fun getSeverityText(): String? {
        return log.severityText
    }

    @Deprecated("Deprecated in Java")
    override fun getBody(): OtelJavaBody {
        return OtelJavaBody.string(checkNotNull(log.body))
    }

    override fun getAttributes(): OtelJavaAttributes {
        val attrBuilder = OtelJavaAttributes.builder()
        log.attributes?.forEach { (key, value) ->
            attrBuilder.put(OtelJavaAttributeKey.stringKey(checkNotNull(key)), checkNotNull(value))
        }
        return attrBuilder.build()
    }

    override fun getTotalAttributeCount(): Int {
        return checkNotNull(log.attributes?.size)
    }
}
