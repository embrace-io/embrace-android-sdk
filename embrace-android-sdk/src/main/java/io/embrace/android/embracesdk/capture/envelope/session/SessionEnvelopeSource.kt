package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal fun interface SessionEnvelopeSource {
    fun getEnvelope(endType: SessionSnapshotType): Envelope<SessionPayload>
}
