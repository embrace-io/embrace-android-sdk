package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

internal fun interface EnvelopeSource<T> {
    fun getEnvelope(endType: SessionSnapshotType): Envelope<T>
}
