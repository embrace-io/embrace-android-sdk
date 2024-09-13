package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionZygote

/**
 * Populates the attributes of a session span.
 */
interface SessionSpanAttrPopulator {

    /**
     * Populates session span attributes at the start of the session.
     */
    fun populateSessionSpanStartAttrs(session: SessionZygote)

    /**
     * Populates session span attributes at the end of the session.
     */
    fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean)
}
