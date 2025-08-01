package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.spans.getEmbraceSpan
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributeKey
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaAttributes
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpan
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaStatusCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceFlags
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaTraceState
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalApi::class)
class FakeOtelJavaSpan(
    val fakeSpanBuilder: FakeSpanBuilder,
) : OtelJavaSpan {

    private val spanContext: OtelJavaSpanContext =
        OtelJavaSpanContext.create(
            fakeSpanBuilder.parentContext.getEmbraceSpan(fakeObjectCreator)?.traceId ?: OtelIds.generateTraceId(),
            OtelIds.generateSpanId(),
            OtelJavaTraceFlags.getDefault(),
            OtelJavaTraceState.getDefault()
        )

    private var isRecording = true
    private var status: OtelJavaStatusCode = OtelJavaStatusCode.UNSET
    private var statusDescription: String = ""

    override fun <T : Any> setAttribute(key: OtelJavaAttributeKey<T>, value: T?): OtelJavaSpan {
        if (value != null) {
            fakeSpanBuilder.setAttribute(key, value)
        }

        return this
    }

    override fun addEvent(name: String, attributes: OtelJavaAttributes): OtelJavaSpan {
        return this
    }

    override fun addEvent(name: String, attributes: OtelJavaAttributes, timestamp: Long, unit: TimeUnit): OtelJavaSpan {
        return this
    }

    override fun setStatus(statusCode: OtelJavaStatusCode, description: String): OtelJavaSpan {
        status = statusCode
        statusDescription = description
        return this
    }

    override fun recordException(exception: Throwable, additionalAttributes: OtelJavaAttributes): OtelJavaSpan {
        return this
    }

    override fun updateName(name: String): OtelJavaSpan {
        return this
    }

    override fun end() {
        isRecording = false
    }

    override fun end(timestamp: Long, unit: TimeUnit): Unit = end()

    override fun getSpanContext(): OtelJavaSpanContext = spanContext

    override fun isRecording(): Boolean = isRecording
}
