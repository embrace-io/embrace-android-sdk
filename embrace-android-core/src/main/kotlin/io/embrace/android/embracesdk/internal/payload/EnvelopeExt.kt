package io.embrace.android.embracesdk.internal.payload

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.spans.findAttributeValue
import io.embrace.android.embracesdk.internal.spans.hasFixedAttribute
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes

public fun Envelope<SessionPayload>.getSessionSpan(): Span? {
    return data.spans?.singleOrNull { it.hasFixedAttribute(EmbType.Ux.Session) }
        ?: data.spanSnapshots?.singleOrNull { it.hasFixedAttribute(EmbType.Ux.Session) }
}

internal fun Envelope<SessionPayload>.getSessionId(): String? {
    return getSessionSpan()?.attributes?.findAttributeValue(SessionIncubatingAttributes.SESSION_ID.key)
}
