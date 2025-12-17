package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey
import io.embrace.android.embracesdk.internal.arch.schema.AppTerminationCause
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.sdk.id.OtelIds
import io.embrace.android.embracesdk.internal.otel.sdk.setEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span

fun Span.hasEmbraceAttribute(embraceAttribute: EmbraceAttribute): Boolean {
    return embraceAttribute.value == attributes?.singleOrNull { it.key == embraceAttribute.key.name }?.data
}

fun Span.hasEmbraceAttributeValue(key: EmbraceAttributeKey, value: Any): Boolean {
    return attributes?.singleOrNull { it.key == key.name }?.data == value.toString()
}

fun Span.toFailedSpan(endTimeMs: Long): Span {
    val newAttributes = mutableMapOf<String, String>().apply {
        setEmbraceAttribute(ErrorCodeAttribute.Failure)
        if (hasEmbraceAttribute(EmbType.Ux.Session)) {
            setEmbraceAttribute(AppTerminationCause.Crash)
        }
    }

    return copy(
        endTimeNanos = endTimeMs.millisToNanos(),
        parentSpanId = parentSpanId ?: OtelIds.INVALID_SPAN_ID,
        status = Span.Status.ERROR,
        attributes = newAttributes.map { Attribute(it.key, it.value) }.plus(attributes ?: emptyList())
    )
}
