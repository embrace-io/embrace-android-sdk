package io.embrace.android.embracesdk.internal.session.orchestrator

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Delegates to [delegate], calling [onFirstSchedule] once after the first task is scheduled but
 * before the caller has published its trigger.
 */
internal class ScheduleHookExecutor(
    private val delegate: ScheduledExecutorService,
    private val onFirstSchedule: () -> Unit,
) : ScheduledExecutorService by delegate {

    private var hooked = false

    override fun schedule(command: Runnable?, delay: Long, unit: TimeUnit?): ScheduledFuture<*> {
        val scheduled = delegate.schedule(command, delay, unit)
        if (!hooked && delay > 0) {
            hooked = true
            onFirstSchedule()
        }
        return scheduled
    }
}
