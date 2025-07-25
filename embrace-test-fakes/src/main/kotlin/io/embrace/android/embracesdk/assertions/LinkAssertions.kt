package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.otel.attrs.asPair
import io.embrace.android.embracesdk.internal.otel.schema.LinkType
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaSpanContext
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes
import org.junit.Assert.assertTrue

fun Link.validatePreviousSessionLink(
    previousSessionSpan: Span,
    previousSessionId: String,
) {
    validateSystemLink(
        linkedSpan = previousSessionSpan,
        type = LinkType.PreviousSession,
        expectedAttributes = mapOf(SessionIncubatingAttributes.SESSION_ID.key to previousSessionId)
    )
}

fun Link.validateSystemLink(
    linkedSpan: Span,
    type: LinkType,
    expectedAttributes: Map<String, String> = emptyMap(),
) {
    validateLinkToSpan(
        linkedSpan = linkedSpan,
        expectedAttributes = mapOf(type.asPair()) + expectedAttributes
    )
}

fun Link.validateLinkToSpan(
    linkedSpan: Span,
    isLinkedSpanRemote: Boolean = false,
    expectedAttributes: Map<String, String> = emptyMap(),
) {
    assertTrue(isLinkedToSpan(linkedSpan, isLinkedSpanRemote))
    checkNotNull(attributes).assertMatches(expectedAttributes)
}

fun Link.validateLinkToSpanContext(
    linkedSpanContext: OtelJavaSpanContext,
    expectedAttributes: Map<String, String> = emptyMap(),
) {
    assertTrue(isLinkedToSpanContext(linkedSpanContext))
    checkNotNull(attributes).assertMatches(expectedAttributes)
}

fun Link.isLinkedToSpan(expectedSpan: Span, expectedIsRemote: Boolean): Boolean =
    traceId == expectedSpan.traceId && spanId == expectedSpan.spanId && isRemote == expectedIsRemote

fun Link.isLinkedToSpanContext(expectedSpanContext: OtelJavaSpanContext): Boolean =
    traceId == expectedSpanContext.traceId && spanId == expectedSpanContext.spanId && isRemote == expectedSpanContext.isRemote
