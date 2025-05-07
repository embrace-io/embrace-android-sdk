package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes

fun Envelope<SessionPayload>.getSessionSpan(): Span? {
    return data.spans?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
        ?: data.spanSnapshots?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
}

fun Envelope<SessionPayload>.getSessionId(): String? {
    return getSessionSpan()?.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key)
}

fun Envelope<SessionPayload>.getSessionProperties(): Map<String, String> {
    return getSessionSpan()?.getSessionProperties() ?: emptyMap()
}

@Suppress("UNCHECKED_CAST")
private fun Span.getSessionProperties(): Map<String, String> =
    attributes?.filter { it.key != null && it.data != null }?.associate { it.key to it.data } as Map<String, String>
