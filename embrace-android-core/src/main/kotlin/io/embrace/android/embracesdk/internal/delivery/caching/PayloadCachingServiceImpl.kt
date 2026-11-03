package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.arch.state.ProcessState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionPartCacher
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import java.util.concurrent.atomic.AtomicBoolean

internal class PayloadCachingServiceImpl(
    private val partCacher: PeriodicSessionPartCacher,
    private val clock: Clock,
    private val sessionIdsProvider: SessionIdsProvider,
    private val payloadStore: PayloadStore,
    private val deliveryTracer: DeliveryTracer? = null,
) : PayloadCachingService {

    private var stateChanged: AtomicBoolean = AtomicBoolean(true)

    override fun shutdown() = partCacher.shutdownAndWait()
    override fun reportBackgroundActivityStateChange() = stateChanged.set(true)

    override fun stopCaching() {
        deliveryTracer?.onCachingStopped()
        partCacher.stop()
        stateChanged.set(true) // reset flag
    }

    override fun startCaching(
        initial: SessionPartToken,
        state: ProcessState,
        snapshotter: SessionPartSnapshotter,
    ) {
        deliveryTracer?.onCachingStarted()
        partCacher.start {
            if (state != ProcessState.BACKGROUND || stateChanged.getAndSet(false)) {
                onSessionCache(initial, state, snapshotter)
            }
            null
        }
    }

    private fun onSessionCache(
        initial: SessionPartToken,
        endProcessState: ProcessState,
        snapshotter: SessionPartSnapshotter,
    ) {
        deliveryTracer?.onSessionCache()

        EmbTrace.trace("sf-periodic-cache-supply") {
            if (!isCurrentPart(initial)) {
                return
            }
            snapshotter(endProcessState, clock.now(), initial) { envelope ->
                // checked again inside the snapshotter's critical section, as the part may have ended since the check above
                if (isCurrentPart(initial)) {
                    payloadStore.cacheSessionPartSnapshot(envelope)
                }
            }
        }
    }

    private fun isCurrentPart(part: SessionPartToken) = part.sessionPartId == sessionIdsProvider.getCurrentSessionPartId()
}
