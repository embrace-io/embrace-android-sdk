package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

interface OtelPayloadMapper {
    /**
     * Return collected data as Spans but outside the OTel SDK pipeline so that it can be used to for payload snapshots or check
     * current state
     */
    fun snapshotSpans(endType: SessionSnapshotType, crashId: String?): List<Span>

    /**
     * Push collected data through the OTel SDK pipeline.
     */
    fun record()
}
