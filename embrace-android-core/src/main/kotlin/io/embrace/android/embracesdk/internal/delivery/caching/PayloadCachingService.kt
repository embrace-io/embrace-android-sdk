package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.delivery.Shutdownable
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken

/**
 * Builds a snapshot of the session part [initial] and hands it to `onSnapshot` inside the critical section that keeps the part
 * from ending meanwhile, so a part's complete payload can never land between building its snapshot and caching it.
 */
typealias SessionPartSnapshotter = (
    state: ProcessState,
    timestamp: Long,
    initial: SessionPartToken,
    onSnapshot: (Envelope<SessionPartPayload>) -> Unit,
) -> Unit

/**
 * This service caches in-memory data in case the process terminates. Cached data from terminated processes will be
 * transformed into a full payloads the next time the SDK starts and delivered to the server.
 */
interface PayloadCachingService : Shutdownable {

    /**
     * Starts caching a payload.
     */
    fun startCaching(
        initial: SessionPartToken,
        state: ProcessState,
        snapshotter: SessionPartSnapshotter,
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
