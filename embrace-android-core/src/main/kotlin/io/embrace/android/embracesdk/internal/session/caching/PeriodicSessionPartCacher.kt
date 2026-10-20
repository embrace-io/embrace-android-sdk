package io.embrace.android.embracesdk.internal.session.caching

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PeriodicSessionPartCacher(
    private val worker: BackgroundWorker,
    private val errorHandler: ErrorHandler,
    private val intervalMs: Long = 2000,
) {

    private var scheduledFuture: ScheduledFuture<*>? = null

    @Volatile
    private var stopped = false

    /**
     * It starts a background job that will schedule a callback to do periodic caching.
     */
    fun start(provider: Provider<Envelope<SessionPartPayload>?>) {
        stopped = false
        scheduledFuture = this.worker.scheduleWithFixedDelay(
            onPeriodicCache(provider),
            0,
            intervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun onPeriodicCache(provider: Provider<Envelope<SessionPartPayload>?>) = Runnable {
        if (stopped) {
            return@Runnable
        }
        EmbTrace.trace("sf-periodic-cache-tick") {
            errorHandler.guardSdkCode(SdkErrorType.PeriodicSessionCacheFail, null, provider)
        }
    }

    fun stop() {
        stopped = true
        scheduledFuture?.cancel(true)
    }

    fun shutdownAndWait() {
        stop()
        worker.shutdownAndWait()
    }
}
