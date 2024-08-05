package io.embrace.android.embracesdk.internal.capture.envelope.session

import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

public interface OtelPayloadMapper {
    public fun getSessionPayload(endType: SessionSnapshotType, crashId: String?): List<Span>
}
