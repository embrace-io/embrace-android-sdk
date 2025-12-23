package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttribute
import io.embrace.android.embracesdk.internal.arch.attrs.EmbraceAttributeKey

/**
 * A type that gives semantic meaning to a Span Link instance, defining the nature of the relationship between the span that contains the
 * link and the span that is being linked to.
 */
sealed class LinkType(
    override val value: String,
) : EmbraceAttribute {
    override val key: EmbraceAttributeKey = EmbraceAttributeKey.create(id = "link_type")

    /**
     * On a session span, it links to the previous valid session span for this app instance.
     */
    object PreviousSession : LinkType("PREV_SESSION")

    /**
     * On a span that is not a session span, it links to the session span of the session in which it ended.
     */
    object EndSession : LinkType("END_SESSION")

    /**
     * On a session span, it links to a span that ended during the session associated this the session span.
     */
    object EndedIn : LinkType("ENDED_IN")

    /**
     * On a session span, it links to a span that represents a State during the course of that session.
     */
    object State : LinkType("STATE")
}
