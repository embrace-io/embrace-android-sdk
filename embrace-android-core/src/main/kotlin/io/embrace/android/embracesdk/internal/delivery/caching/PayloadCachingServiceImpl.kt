package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionToken
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import java.util.concurrent.atomic.AtomicBoolean

internal class PayloadCachingServiceImpl(
    private val periodicSessionCacher: PeriodicSessionCacher,
    private val clock: Clock,
    private val sessionTracker: SessionTracker,
    private val payloadStore: PayloadStore,
    private val deliveryTracer: DeliveryTracer? = null,
) : PayloadCachingService {

    private var stateChanged: AtomicBoolean = AtomicBoolean(true)

    override fun shutdown() = periodicSessionCacher.shutdownAndWait()
    override fun reportBackgroundActivityStateChange() = stateChanged.set(true)

    override fun stopCaching() {
        deliveryTracer?.onCachingStopped()
        periodicSessionCacher.stop()
        stateChanged.set(true) // reset flag
    }

    override fun startCaching(
        initial: SessionToken,
        state: AppState,
        supplier: SessionPayloadSupplier,
    ) {
        deliveryTracer?.onCachingStarted()
        periodicSessionCacher.start {
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
        initial: SessionToken,
        endAppState: AppState,
        supplier: SessionPayloadSupplier,
    ): Envelope<SessionPayload>? {
        deliveryTracer?.onSessionCache()

        EmbTrace.trace("on-session-cache") {
            if (initial.sessionId != sessionTracker.getActiveSessionId()) {
                return null
            }
            return supplier(endAppState, clock.now(), initial)?.apply {
                payloadStore.cacheSessionSnapshot(this)
            }
        }
    }
}
