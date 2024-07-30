package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

internal class FakeSessionPayloadSource : SessionPayloadSource {

    var sessionPayload: SessionPayload = SessionPayload()
    override fun getSessionPayload(
        endType: SessionSnapshotType,
        startNewSession: Boolean,
        crashId: String?
    ) = sessionPayload
}
