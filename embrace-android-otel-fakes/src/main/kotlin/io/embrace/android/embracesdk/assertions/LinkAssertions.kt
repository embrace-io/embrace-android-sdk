
package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.schema.LinkType
import io.embrace.android.embracesdk.internal.payload.Link
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import io.opentelemetry.kotlin.tracing.SpanContext
import org.junit.Assert.assertTrue

fun Link.validatePreviousSessionPartLink(
    previousSessionSpan: Span,
    previousSessionPartId: String,
    previousUserSessionId: String? = null,
) {
    val expected = buildMap {
        put(EmbSessionAttributes.EMB_SESSION_PART_ID, previousSessionPartId)
        previousUserSessionId?.let {
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, it)
            put(SessionAttributes.SESSION_ID, it)
        }
    }
    validateSystemLink(
        linkedSpan = previousSessionSpan,
        type = LinkType.PreviousSession,
        expectedAttributes = expected
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
    linkedSpanContext: SpanContext,
    expectedAttributes: Map<String, String> = emptyMap(),
) {
    assertTrue(isLinkedToSpanContext(linkedSpanContext))
    checkNotNull(attributes).assertMatches(expectedAttributes)
}

fun Link.isLinkedToSpan(expectedSpan: Span, expectedIsRemote: Boolean): Boolean =
    traceId == expectedSpan.traceId && spanId == expectedSpan.spanId && isRemote == expectedIsRemote

fun Link.isLinkedToSpanContext(expectedSpanContext: SpanContext): Boolean =
    traceId == expectedSpanContext.traceId && spanId == expectedSpanContext.spanId && isRemote == expectedSpanContext.isRemote
