package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

public class FakeSessionPayloadSource : SessionPayloadSource {

    public var sessionPayload: SessionPayload = SessionPayload()
    public var lastStartNewSession: Boolean? = null

    override fun getSessionPayload(
        endType: SessionSnapshotType,
        startNewSession: Boolean,
        crashId: String?
    ): SessionPayload {
        lastStartNewSession = startNewSession
        return SessionPayload()
    }
}
