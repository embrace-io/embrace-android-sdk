package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.arch.attrs.isEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.opentelemetry.kotlin.semconv.SessionAttributes

fun Envelope<SessionPartPayload>.getSessionSpan(): Span? {
    return data.spans?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
        ?: data.spanSnapshots?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
}

fun Envelope<SessionPartPayload>.getStateSpan(spanName: String): Span? {
    return data.spans?.singleOrNull { it.hasEmbraceAttribute(EmbType.State) && it.name == spanName }
}

fun Envelope<SessionPartPayload>.getSessionId(): String? {
    return getSessionSpan()?.attributes?.findAttributeValue(SessionAttributes.SESSION_ID)
}

fun Envelope<SessionPartPayload>.getSessionProperties(): Map<String, String> {
    return getSessionSpan()?.getSessionProperties() ?: emptyMap()
}

@Suppress("UNCHECKED_CAST")
private fun Span.getSessionProperties(): Map<String, String> =
    attributes
        ?.filter {
            val keyName = it.key
            keyName != null && keyName.isEmbraceAttributeName() && it.data != null
        }?.associate {
            it.key to it.data
        } as Map<String, String>
