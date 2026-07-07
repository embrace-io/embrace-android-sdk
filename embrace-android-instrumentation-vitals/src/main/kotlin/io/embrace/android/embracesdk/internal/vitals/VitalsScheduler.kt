package io.embrace.android.embracesdk.internal.vitals

import android.os.Handler
import android.os.HandlerThread

internal interface VitalsScheduler {
    fun post(action: Runnable)
    fun scheduleSettle(delayMs: Long, action: Runnable)
    fun cancelSettle(action: Runnable)
}

internal class HandlerVitalsScheduler : VitalsScheduler {

    private var handlerThread: HandlerThread? = null

    /**
     * Handler backed by the dedicated vitals thread. Valid only between [start] and [stop]; also used by
     * the data source for the frame-metrics and display-change callbacks so everything shares one thread.
     */
    lateinit var handler: Handler
        private set

    /**
     * Starts the backing thread and prepares its [handler].
     */
    fun start() {
        val thread = HandlerThread(HANDLER_THREAD_NAME).apply { start() }
        handlerThread = thread
        handler = Handler(thread.looper)
    }

    /**
     * Cancels all pending settles and stops the backing thread.
     */
    fun stop() {
        handlerThread?.let { thread ->
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
        }
        handlerThread = null
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
