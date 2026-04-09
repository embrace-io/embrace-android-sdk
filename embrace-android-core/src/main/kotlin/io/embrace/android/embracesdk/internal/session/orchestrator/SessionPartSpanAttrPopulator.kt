package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken

/**
 * Populates the attributes of a session span.
 */
interface SessionPartSpanAttrPopulator {

    /**
     * Populates session span attributes at the start of the session.
     */
    fun populateSessionSpanStartAttrs(session: SessionPartToken)

    /**
     * Populates session span attributes at the end of the session.
     */
    fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean)
}
