package io.embrace.android.embracesdk.internal.vitals

import android.os.Handler

internal interface VitalsScheduler {
    fun post(action: Runnable)
    fun scheduleSettle(delayMs: Long, action: Runnable)
    fun cancelSettle()
}

internal class HandlerVitalsScheduler(private val handler: Handler) : VitalsScheduler {

    private var settle: Runnable? = null

    override fun post(action: Runnable) {
        handler.post(action)
    }

    override fun scheduleSettle(delayMs: Long, action: Runnable) {
        cancelSettle()
        settle = action
        handler.postDelayed(action, delayMs)
    }

    override fun cancelSettle() {
        settle?.let(handler::removeCallbacks)
        settle = null
    }
}
