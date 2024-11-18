package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import java.util.concurrent.atomic.AtomicBoolean

internal class PayloadCachingServiceImpl(
    private val periodicSessionCacher: PeriodicSessionCacher,
    private val clock: Clock,
    private val sessionIdTracker: SessionIdTracker,
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
        initial: SessionZygote,
        state: ProcessState,
        supplier: SessionPayloadSupplier,
    ) {
        deliveryTracer?.onCachingStarted()
        periodicSessionCacher.start {
            if (state == ProcessState.BACKGROUND) {
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
        initial: SessionZygote,
        endProcessState: ProcessState,
        supplier: SessionPayloadSupplier,
    ): Envelope<SessionPayload>? {
        deliveryTracer?.onSessionCache()

        Systrace.traceSynchronous("on-session-cache") {
            if (initial.sessionId != sessionIdTracker.getActiveSessionId()) {
                return null
            }
            return supplier(endProcessState, clock.now(), initial)?.apply {
                payloadStore.cacheSessionSnapshot(this)
            }
        }
    }
}
