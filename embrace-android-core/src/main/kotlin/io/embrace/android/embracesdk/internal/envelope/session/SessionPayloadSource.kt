package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType

/**
 * Creates a [SessionPayload] object.
 */
public interface SessionPayloadSource {
    public fun getSessionPayload(
        endType: SessionSnapshotType,
        startNewSession: Boolean,
        crashId: String? = null
    ): SessionPayload
}
