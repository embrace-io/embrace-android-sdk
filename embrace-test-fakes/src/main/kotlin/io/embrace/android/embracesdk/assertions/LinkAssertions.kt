package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.otel.payload.toEmbracePayload
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertEquals

fun Link.validateSystemLink(
    linkedSpan: Span,
    type: LinkType
) {
    assertEquals(linkedSpan.traceId, traceId)
    assertEquals(linkedSpan.spanId, spanId)
    assertEquals(false, isRemote)
    assertEquals(type.value, checkNotNull(attributes).toEmbracePayload()[type.key.name])
}

fun Link.validatePreviousSessionLink(
    previousSessionSpan: Span,
    previousSessionId: String,
) {
    validateSystemLink(previousSessionSpan, LinkType.PreviousSession)
    val attrs = checkNotNull(attributes).toEmbracePayload()
    assertEquals(2, attrs.size)
    assertEquals(previousSessionId, attrs[SessionIncubatingAttributes.SESSION_ID.key])
}

fun Link.validateCustomLink(
    linkedSpan: Span,
    isLinkedSpanRemote: Boolean = false,
    expectedAttributes: Map<String, String> = emptyMap()
) {
    assertEquals(linkedSpan.traceId, traceId)
    assertEquals(linkedSpan.spanId, spanId)
    assertEquals(isLinkedSpanRemote, isRemote)
    checkNotNull(attributes).assertMatches(expectedAttributes)
}
