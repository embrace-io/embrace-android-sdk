package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.payload.SessionPayload

/**
 * Interface that hides the details of how session payloads are stored to callers. This is
 * a shim that hides whether v1 or v2 of the storage implementation is used. Once we delete
 * v1, this interface can be deleted too.
 */
interface PayloadStore : CrashTeardownHandler {

    /**
     * Stores a final session payload that will have no further modifications
     * (i.e. the session ended or crashed)
     */
    fun storeSessionPayload(envelope: Envelope<SessionPayload>, transitionType: TransitionType)

    /**
     * Stores a session snapshot that is likely to have further modifications.
     */
    fun cacheSessionSnapshot(envelope: Envelope<SessionPayload>)

    /**
     * Stores a log payload that will have no further modifications.
     */
    fun storeLogPayload(envelope: Envelope<LogPayload>, attemptImmediateRequest: Boolean)

    /**
     * Stores an empty payload-type-less crash envelope for future use. One one cached version of this should
     * exist at one time.
     */
    fun cacheEmptyCrashEnvelope(envelope: Envelope<LogPayload>)

    /**
     * Handles graceful shutdown on a crash. This should be called _after_ any payloads for the
     * crash have been added. The legacy implementation ignores this but the v2 implementation
     * relies on it.
     */
    override fun handleCrash(crashId: String)
}
