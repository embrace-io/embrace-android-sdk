package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionPartCacher
import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import java.util.concurrent.atomic.AtomicBoolean

internal class PayloadCachingServiceImpl(
    private val partCacher: PeriodicSessionPartCacher,
    private val clock: Clock,
    private val sessionIdProvider: SessionIdProvider,
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
        state: AppState,
        supplier: SessionPartPayloadSupplier,
    ) {
        deliveryTracer?.onCachingStarted()
        partCacher.start {
            if (state == AppState.BACKGROUND) {
                if (stateChanged.getAndSet(false)) {
                    onSessionCache(initial, state, supplier)
                } else {
                    null
                }
            } else {
                onSessionCache(initial, state, supplier)
            }
        }
    }

    private fun onSessionCache(
        initial: SessionPartToken,
        endAppState: AppState,
        supplier: SessionPartPayloadSupplier,
    ): Envelope<SessionPartPayload>? {
        deliveryTracer?.onSessionCache()

        EmbTrace.trace("on-session-cache") {
            if (initial.sessionId != sessionIdProvider.getCurrentSessionPartId()) {
                return null
            }
            return supplier(endAppState, clock.now(), initial)?.apply {
                payloadStore.cacheSessionPartSnapshot(this)
            }
        }
    }
}
