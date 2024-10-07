package io.embrace.android.embracesdk.internal.delivery.caching

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import java.util.concurrent.atomic.AtomicBoolean

internal class PayloadCachingServiceImpl(
    private val periodicSessionCacher: PeriodicSessionCacher
) : PayloadCachingService {

    private var stateChanged: AtomicBoolean = AtomicBoolean(true)

    override fun shutdown() = periodicSessionCacher.shutdownAndWait()
    override fun reportBackgroundActivityStateChange() = stateChanged.set(true)

    override fun stopCaching() {
        periodicSessionCacher.stop()
        stateChanged.set(true) // reset flag
    }

    override fun startCaching(
        state: ProcessState,
        supplier: () -> Envelope<SessionPayload>?,
    ) {
        periodicSessionCacher.start {
            return@start if (state == ProcessState.BACKGROUND) {
                if (stateChanged.getAndSet(false)) {
                    supplier()
                } else {
                    null
                }
            } else {
                supplier()
            }
        }
    }
}
