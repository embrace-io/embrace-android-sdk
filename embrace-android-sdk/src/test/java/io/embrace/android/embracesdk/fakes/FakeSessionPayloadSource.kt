package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.session.SessionPayloadSource
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal class FakeSessionPayloadSource : SessionPayloadSource {

    var sessionPayload: SessionPayload = SessionPayload()
    var lastStartNewSession: Boolean? = null

    override fun getSessionPayload(
        endType: SessionSnapshotType,
        startNewSession: Boolean,
        crashId: String?
    ): SessionPayload {
        lastStartNewSession = startNewSession
        return SessionPayload()
    }
}
