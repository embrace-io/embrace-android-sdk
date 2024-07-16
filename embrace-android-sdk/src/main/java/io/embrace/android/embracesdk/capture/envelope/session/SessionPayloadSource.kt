package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

/**
 * Creates a [SessionPayload] object.
 */
internal interface SessionPayloadSource {
    fun getSessionPayload(endType: SessionSnapshotType, startNewSession: Boolean, crashId: String? = null): SessionPayload
}
