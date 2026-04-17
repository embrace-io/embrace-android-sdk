package io.embrace.android.embracesdk.internal.session.orchestrator

import java.util.concurrent.ScheduledFuture

/**
 * Holds the state of a session-scoped timer (e.g. inactivity timeout, max duration).
 * [cancel] cancels any pending timer future.
 */
internal class SessionTimerState(
    private val future: ScheduledFuture<*>,
) {
    fun cancel() {
        runCatching {
            future.cancel(false)
        }
    }
}
