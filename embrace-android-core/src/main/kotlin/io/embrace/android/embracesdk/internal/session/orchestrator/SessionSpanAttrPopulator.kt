package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionZygote

/**
 * Populates the attributes of a session span.
 */
public interface SessionSpanAttrPopulator {

    /**
     * Populates session span attributes at the start of the session.
     */
    public fun populateSessionSpanStartAttrs(session: SessionZygote)

    /**
     * Populates session span attributes at the end of the session.
     */
    public fun populateSessionSpanEndAttrs(endType: LifeEventType?, crashId: String?, coldStart: Boolean)
}
