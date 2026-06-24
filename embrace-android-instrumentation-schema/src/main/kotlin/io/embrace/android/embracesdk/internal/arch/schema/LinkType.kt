package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.semconv.EmbSpanAttributes

/**
 * A type that gives semantic meaning to a Span Link instance, defining the nature of the relationship between the span that contains the
 * link and the span that is being linked to.
 */
sealed class LinkType(
    override val value: String,
) : EmbraceAttribute {
    override val key: String = EmbSpanAttributes.EMB_LINK_TYPE

    /**
     * On a session part span, it links to the previous valid session part span for this app instance.
     */
    object PreviousSessionPart : LinkType("PREV_SESSION_PART")

    /**
     * On a span that is not a session part span, it links to the session part span of the session part in which it ended.
     */
    object EndSessionPart : LinkType("END_SESSION_PART")

    /**
     * On a session part span, it links to a span that ended during the session part associated with this the session part span.
     */
    object EndedIn : LinkType("ENDED_IN")

    /**
     * On a session part span, it links to a span that represents a State during the course of that session.
     */
    object State : LinkType("STATE")
}
