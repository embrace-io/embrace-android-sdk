package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

interface OtelPayloadMapper {
    fun getSessionPayload(endType: SessionSnapshotType, crashId: String?): List<Span>
}
