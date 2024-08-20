package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.payload.SessionZygote
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSpanAttrPopulator

public class FakeSessionSpanAttrPopulator : SessionSpanAttrPopulator {

    override fun populateSessionSpanStartAttrs(session: SessionZygote) {
    }

    override fun populateSessionSpanEndAttrs(
        endType: LifeEventType?,
        crashId: String?,
        coldStart: Boolean
    ) {
    }
}
