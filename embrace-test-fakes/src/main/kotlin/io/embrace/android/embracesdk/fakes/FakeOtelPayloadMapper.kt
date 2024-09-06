package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

class FakeOtelPayloadMapper : OtelPayloadMapper {
    override fun getSessionPayload(endType: SessionSnapshotType, crashId: String?): List<Span> = emptyList()
}
