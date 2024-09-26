package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload

/**
 * Interface that hides the details of how session payloads are stored to callers. This is
 * a shim that hides whether v1 or v2 of the storage implementation is used. Once we delete
 * v1, this interface can be deleted too.
 */
interface SessionPayloadStore {

    /**
     * Stores a final session payload that will have no further modifications
     * (i.e. the session ended or crashed)
     */
    fun storeSessionPayload(envelope: Envelope<SessionPayload>, transitionType: TransitionType)

    /**
     * Stores a session snapshot that is likely to have further modifications.
     */
    fun cacheSessionSnapshot(envelope: Envelope<SessionPayload>)
}
