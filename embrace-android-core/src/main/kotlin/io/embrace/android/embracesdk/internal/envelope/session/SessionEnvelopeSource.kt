package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

interface SessionEnvelopeSource {
    fun getEnvelope(
        endType: SessionSnapshotType,
        startNewSession: Boolean,
        crashId: String? = null
    ): Envelope<SessionPayload>
}
