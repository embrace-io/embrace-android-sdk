package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata

/**
 * Populates the attributes of a session part span.
 */
interface SessionPartSpanAttrPopulator {

    /**
     * Populates session part span attributes at the start of the session.
     */
    fun populateSessionPartSpanStartAttrs(sessionPart: SessionPartToken, userSession: UserSessionMetadata?)

    /**
     * Populates session part span attributes at the end of the session.
     */
    fun populateSessionPartSpanEndAttrs(
        endType: LifeEventType?,
        crashId: String?,
        coldStart: Boolean,
        endAttributes: Map<String, String>,
    )
}
