package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.internal.otel.sdk.toStringMap
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaScope
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaStatusCode
import io.embrace.opentelemetry.kotlin.k2j.tracing.convertToOtelKotlin
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
class EmbOtelJavaSpan(
    private val embraceSpan: EmbraceSdkSpan,
    private val clock: Clock,
) : OtelJavaSpan {

    override fun <T : Any> setAttribute(key: OtelJavaAttributeKey<T>, value: T?): OtelJavaSpan {
        embraceSpan.addAttribute(key = key.key, value = value.toString())
        return this
    }

    override fun addEvent(name: String, attributes: OtelJavaAttributes): OtelJavaSpan = addEvent(
        name = name,
        attributes = attributes,
        timestamp = clock.now(),
        unit = TimeUnit.NANOSECONDS
    )

    override fun addEvent(name: String, attributes: OtelJavaAttributes, timestamp: Long, unit: TimeUnit): OtelJavaSpan {
        embraceSpan.addEvent(
            name = name,
            timestampMs = unit.toMillis(timestamp),
            attributes = attributes.toStringMap()
        )
        return this
    }

    override fun addLink(spanContext: OtelJavaSpanContext, attributes: OtelJavaAttributes): OtelJavaSpan {
        embraceSpan.addLink(spanContext, attributes.toStringMap())
        return this
    }

    override fun setStatus(statusCode: OtelJavaStatusCode, description: String): OtelJavaSpan {
        if (isRecording) {
            embraceSpan.setStatus(statusCode.convertToOtelKotlin(), description)
        }
        return this
    }

    override fun recordException(exception: Throwable, additionalAttributes: OtelJavaAttributes): OtelJavaSpan {
        embraceSpan.recordException(exception, additionalAttributes.toStringMap())
        return this
    }

    override fun updateName(name: String): OtelJavaSpan {
        embraceSpan.updateName(name)
        return this
    }

    override fun end(): Unit = end(timestamp = clock.now(), unit = TimeUnit.NANOSECONDS)

    override fun end(timestamp: Long, unit: TimeUnit) {
        if (isRecording) {
            embraceSpan.stop(endTimeMs = unit.toMillis(timestamp))
        }
    }

    override fun getSpanContext(): OtelJavaSpanContext = embraceSpan.spanContext ?: OtelJavaSpanContext.getInvalid()

    override fun isRecording(): Boolean = embraceSpan.isRecording

    override fun makeCurrent(): OtelJavaScope = OtelJavaContext.current().with(this).with(embraceSpan).makeCurrent()
}
