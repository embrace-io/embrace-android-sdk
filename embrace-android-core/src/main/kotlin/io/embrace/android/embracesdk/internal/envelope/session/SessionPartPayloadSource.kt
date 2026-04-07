package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType

/**
 * Creates a [SessionPartPayload] object.
 */
interface SessionPartPayloadSource {
    fun getSessionPartPayload(
        endType: SessionPartSnapshotType,
        startNewSession: Boolean,
        crashId: String? = null,
    ): SessionPartPayload
}
