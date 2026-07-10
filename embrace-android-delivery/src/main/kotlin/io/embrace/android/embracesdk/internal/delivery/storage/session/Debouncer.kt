package io.embrace.android.embracesdk.internal.delivery.storage.session

import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Coalesces frequent write requests so that e.g. a burst of span updates results in a single
 * snapshot rewrite. Each keyed action fires at most once per [delayMs] after the latest request.
 */
class Debouncer(private val delayMs: Long = 500L) {

    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "emb-session-debouncer").apply { isDaemon = true }
    }
    private val pending = HashMap<String, ScheduledFuture<*>>()

    @Synchronized
    fun submit(key: String, action: () -> Unit) {
        pending.remove(key)?.cancel(false)
        try {
            pending[key] = executor.schedule({ runAndClear(key, action) }, delayMs, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            // the debouncer has been shut down (the session part is finalizing) - drop the write.
        }
    }

    @Synchronized
    private fun runAndClear(key: String, action: () -> Unit) {
        pending.remove(key)
        action()
    }

    /**
     * Lets any already-scheduled write run to completion, then terminates. Callers can safely
     * perform a final synchronous write afterwards knowing no debounced write will race it.
     */
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private companion object {
        private const val SHUTDOWN_TIMEOUT_MS = 2_000L
    }
}
