package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

interface SessionPartEnvelopeSource {
    fun getEnvelope(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String? = null,
    ): Envelope<SessionPartPayload>
}
