package io.embrace.android.embracesdk.internal.session.orchestrator

import java.util.concurrent.ScheduledFuture

/**
 * Holds the state of the inactivity timer for a background period.
 * [cancel] cancels any pending timer future.
 */
internal class InactivityTimerState(
    private val future: ScheduledFuture<*>,

    @Volatile
    var exceeded: Boolean = false,
) {
    fun cancel() {
        runCatching {
            future.cancel(false)
        }
    }
}
