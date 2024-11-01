package io.embrace.android.embracesdk.internal.session.caching

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PeriodicSessionCacher(
    private val worker: BackgroundWorker,
    private val logger: EmbLogger,
    private val intervalMs: Long = 2000,
) {

    private var scheduledFuture: ScheduledFuture<*>? = null

    /**
     * It starts a background job that will schedule a callback to do periodic caching.
     */
    fun start(provider: Provider<Envelope<SessionPayload>?>) {
        scheduledFuture = this.worker.scheduleWithFixedDelay(
            onPeriodicCache(provider),
            0,
            intervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun onPeriodicCache(provider: Provider<Envelope<SessionPayload>?>) = Runnable {
        Systrace.traceSynchronous("snapshot-session") {
            try {
                provider()
            } catch (ex: Exception) {
                logger.trackInternalError(InternalErrorType.FG_SESSION_CACHE_FAIL, ex)
            }
        }
    }

    fun stop() {
        scheduledFuture?.cancel(false)
    }

    fun shutdownAndWait() {
        stop()
        worker.shutdownAndWait()
    }
}
