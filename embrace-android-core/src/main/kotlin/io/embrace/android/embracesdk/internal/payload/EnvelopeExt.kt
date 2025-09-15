package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes

fun Envelope<SessionPayload>.getSessionSpan(): Span? {
    return data.spans?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
        ?: data.spanSnapshots?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
}

@OptIn(IncubatingApi::class)
fun Envelope<SessionPayload>.getSessionId(): String? {
    return getSessionSpan()?.attributes?.findAttributeValue(SessionAttributes.SESSION_ID)
}

fun Envelope<SessionPayload>.getSessionProperties(): Map<String, String> {
    return getSessionSpan()?.getSessionProperties() ?: emptyMap()
}

@Suppress("UNCHECKED_CAST")
private fun Span.getSessionProperties(): Map<String, String> =
    attributes?.filter { it.key != null && it.data != null }?.associate { it.key to it.data } as Map<String, String>
