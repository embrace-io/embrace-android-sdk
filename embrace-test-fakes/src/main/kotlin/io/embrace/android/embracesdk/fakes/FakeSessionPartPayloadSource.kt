package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.session.SessionPartPayloadSource
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

class FakeSessionPartPayloadSource : SessionPartPayloadSource {

    var sessionPayload: SessionPartPayload = SessionPartPayload()
    var lastStartNewSession: Boolean? = null
    var payloadBuiltCount: Int = 0
    var endedWithoutPayloadCount: Int = 0

    override fun getSessionPartPayload(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ): SessionPartPayload {
        payloadBuiltCount++
        lastStartNewSession = startNewSession
        return SessionPartPayload()
    }

    override fun endSessionPart(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String?,
    ) {
        endedWithoutPayloadCount++
        lastStartNewSession = startNewSession
    }
}
