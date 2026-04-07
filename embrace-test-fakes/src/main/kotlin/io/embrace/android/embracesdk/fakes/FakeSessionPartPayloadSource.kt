package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

class FakeSessionPartPayloadSource : SessionPartPayloadSource {

    var sessionPayload: SessionPartPayload = SessionPartPayload()
    var lastStartNewSession: Boolean? = null

    override fun getSessionPartPayload(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ): SessionPartPayload {
        lastStartNewSession = startNewSession
        return SessionPartPayload()
    }
}
