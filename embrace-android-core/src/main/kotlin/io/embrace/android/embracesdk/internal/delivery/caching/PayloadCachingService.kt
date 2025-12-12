package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionToken

typealias SessionPayloadSupplier = (
    state: AppState,
    timestamp: Long,
    initial: SessionToken,
) -> Envelope<SessionPayload>?

/**
 * This service caches in-memory data in case the process terminates. Cached data from terminated processes will be
 * transformed into a full payloads the next time the SDK starts and delivered to the server.
 */
interface PayloadCachingService : Shutdownable {

    /**
     * Starts caching a payload.
     */
    fun startCaching(
        initial: SessionToken,
        state: AppState,
        supplier: SessionPayloadSupplier,
    )

    /**
     * Stops caching a payload.
     */
    fun stopCaching()

    /**
     * Reports that the state of the background activity has changed.
     */
    fun reportBackgroundActivityStateChange()
}
