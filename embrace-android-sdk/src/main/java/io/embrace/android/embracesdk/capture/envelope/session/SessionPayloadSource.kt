package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType

/**
 * Creates a [SessionPayload] object.
 */
internal fun interface SessionPayloadSource {
    fun getSessionPayload(endType: SessionSnapshotType): SessionPayload
}
