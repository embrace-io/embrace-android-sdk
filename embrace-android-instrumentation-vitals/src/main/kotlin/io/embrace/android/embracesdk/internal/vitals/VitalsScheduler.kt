package io.embrace.android.embracesdk.internal.vitals

import android.os.Handler
import android.os.HandlerThread

internal interface VitalsScheduler {
    fun post(action: Runnable)
    fun scheduleSettle(delayMs: Long, action: Runnable)
    fun cancelSettle()
}

internal class HandlerVitalsScheduler : VitalsScheduler {

    private var handlerThread: HandlerThread? = null

    /**
     * Handler backed by the dedicated vitals thread. Valid only between [start] and [stop]; also used by
     * the data source for the frame-metrics and display-change callbacks so everything shares one thread.
     */
    lateinit var handler: Handler
        private set

    private var settle: Runnable? = null

    /**
     * Starts the backing thread and prepares its [handler].
     */
    fun start() {
        val thread = HandlerThread(HANDLER_THREAD_NAME).apply { start() }
        handlerThread = thread
        handler = Handler(thread.looper)
    }

    /**
     * Cancels any pending settle and stops the backing thread.
     */
    fun stop() {
        cancelSettle()
        handlerThread?.quitSafely()
        handlerThread = null
    }

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

    private companion object {
        const val HANDLER_THREAD_NAME = "emb-vitals"
    }
}
