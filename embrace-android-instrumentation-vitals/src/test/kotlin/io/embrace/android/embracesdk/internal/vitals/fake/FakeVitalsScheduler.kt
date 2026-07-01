package io.embrace.android.embracesdk.internal.vitals.fake

import android.os.SystemClock
import io.embrace.android.embracesdk.internal.vitals.VitalsScheduler
import java.util.IdentityHashMap

/**
 * Records what was scheduled and lets the test run the pending settle check.
 */
internal class FakeVitalsScheduler : VitalsScheduler {
    private val schedules = IdentityHashMap<Runnable, Long>()
    val scheduled: Boolean get() = schedules.isNotEmpty()
    val scheduledTaskCount: Int get() = schedules.size

    override fun post(action: Runnable) = action.run()

    override fun scheduleSettle(delayMs: Long, action: Runnable) {
        schedules[action] = SystemClock.uptimeMillis() + delayMs
    }

    override fun cancelSettle(action: Runnable) {
        schedules.remove(action)
    }

    fun runPending() {
        val tasks = schedules.keys.toList()
        schedules.clear()

        tasks.forEach { action ->
            action.run()
        }
    }

    fun runDue(time: Long = SystemClock.uptimeMillis()) {
        val tasks = schedules.filter { (_, scheduledTime) -> scheduledTime <= time }.map { it.key }
        tasks.forEach { schedules.remove(it) }
        tasks.forEach { it.run() }
    }
}
