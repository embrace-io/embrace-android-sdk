package io.embrace.android.embracesdk.internal.vitals

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

internal interface VitalsScheduler {
    fun post(action: Runnable)
    fun scheduleSettle(delayMs: Long, action: Runnable)
    fun cancelSettle(action: Runnable)
}

internal class HandlerVitalsScheduler(private val errorHandler: ErrorHandler) : VitalsScheduler {

    /**
     * Handler backed by the dedicated vitals thread. Valid only after [start]; also used by
     * the data source for the frame-metrics and display-change callbacks so everything shares one thread.
     * Catches anything thrown by dispatched work so it can't kill the thread or the app.
     */
    lateinit var handler: Handler
        private set

    private var thread: HandlerThread? = null

    @Volatile
    private var stopped = false

    /**
     * Starts the backing thread and prepares its [handler].
     */
    fun start() {
        val thread = HandlerThread(HANDLER_THREAD_NAME).apply { start() }
        this.thread = thread
        handler = object : Handler(thread.looper) {
            override fun dispatchMessage(msg: Message) {
                errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) { super.dispatchMessage(msg) }
            }
        }
    }

    /**
     * Stops the backing thread. Later calls to schedule work are dropped rather than posted to a dead thread.
     */
    fun stop() {
        stopped = true
        thread?.quitSafely()
        thread = null
    }

    override fun post(action: Runnable) {
        if (!stopped) {
            handler.post(action)
        }
    }

    override fun scheduleSettle(delayMs: Long, action: Runnable) {
        if (!stopped) {
            handler.removeCallbacks(action)
            handler.postDelayed(action, delayMs)
        }
    }

    override fun cancelSettle(action: Runnable) {
        handler.removeCallbacks(action)
    }

    private companion object {
        const val HANDLER_THREAD_NAME = "emb-vitals"
    }
}
