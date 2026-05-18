package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata

/**
 * Populates the attributes of a session span.
 */
interface SessionPartSpanAttrPopulator {

    /**
     * Populates session span attributes at the start of the session.
     */
    fun populateSessionSpanStartAttrs(sessionPart: SessionPartToken, userSession: UserSessionMetadata?)

    /**
     * Populates session span attributes at the end of the session.
     */
    fun populateSessionSpanEndAttrs(
        endType: LifeEventType?,
        crashId: String?,
        coldStart: Boolean,
        endAttributes: Map<String, String>,
    )
}
