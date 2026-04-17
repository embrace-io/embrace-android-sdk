package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSpanAttrPopulator

class FakeSessionPartSpanAttrPopulator : SessionPartSpanAttrPopulator {

    override fun populateSessionSpanStartAttrs(sessionPart: SessionPartToken, userSession: UserSessionMetadata) {
    }

    override fun populateSessionSpanEndAttrs(
        endType: LifeEventType?,
        crashId: String?,
        coldStart: Boolean,
        endAttributes: Map<String, String>,
    ) {
    }
}
