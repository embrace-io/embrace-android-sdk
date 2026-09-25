package io.embrace.android.embracesdk.internal.vitals

import android.os.Handler
import android.os.HandlerThread

internal interface VitalsScheduler {
    fun post(action: Runnable)
    fun scheduleSettle(delayMs: Long, action: Runnable)
    fun cancelSettle(action: Runnable)
}

internal class HandlerVitalsScheduler : VitalsScheduler {

    /**
     * Handler backed by the dedicated vitals thread. Valid only after [start]; also used by
     * the data source for the frame-metrics and display-change callbacks so everything shares one thread.
     */
    lateinit var handler: Handler
        private set

    /**
     * Starts the backing thread and prepares its [handler].
     */
    fun start() {
        val thread = HandlerThread(HANDLER_THREAD_NAME).apply { start() }
        handler = Handler(thread.looper)
    }

    override fun post(action: Runnable) {
        handler.post(action)
    }

    override fun scheduleSettle(delayMs: Long, action: Runnable) {
        handler.removeCallbacks(action)
        handler.postDelayed(action, delayMs)
    }

    override fun cancelSettle(action: Runnable) {
        handler.removeCallbacks(action)
    }

    private companion object {
        const val HANDLER_THREAD_NAME = "emb-vitals"
    }
}
