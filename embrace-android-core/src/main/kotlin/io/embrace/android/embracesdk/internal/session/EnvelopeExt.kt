package io.embrace.android.embracesdk.internal.session

import io.embrace.android.embracesdk.internal.arch.attrs.isEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.otel.spans.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.opentelemetry.kotlin.semconv.IncubatingApi
import io.embrace.opentelemetry.kotlin.semconv.SessionAttributes

fun Envelope<SessionPayload>.getSessionSpan(): Span? {
    return data.spans?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
        ?: data.spanSnapshots?.singleOrNull { it.hasEmbraceAttribute(EmbType.Ux.Session) }
}

fun Envelope<SessionPayload>.getStateSpan(spanName: String): Span? {
    return data.spans?.singleOrNull { it.hasEmbraceAttribute(EmbType.State) && it.name == spanName }
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
    attributes
        ?.filter {
            val keyName = it.key
            keyName != null && keyName.isEmbraceAttributeName() && it.data != null
        }?.associate {
            it.key to it.data
        } as Map<String, String>
