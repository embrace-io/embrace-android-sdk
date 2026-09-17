package io.embrace.android.embracesdk.internal.session.orchestrator

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Refuses the first schedule, as a worker that has been shut down does.
 */
internal class FailingScheduleExecutor(
    private val delegate: ScheduledExecutorService,
) : ScheduledExecutorService by delegate {

    private var failed = false

    override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
        if (!failed) {
            failed = true
            error("worker will not take the task")
        }
        return delegate.schedule(command, delay, unit)
    }
}
