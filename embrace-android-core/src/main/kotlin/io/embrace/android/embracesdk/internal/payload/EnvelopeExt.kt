package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.getSessionProperties
import io.embrace.android.embracesdk.internal.spans.hasEmbraceAttribute
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
