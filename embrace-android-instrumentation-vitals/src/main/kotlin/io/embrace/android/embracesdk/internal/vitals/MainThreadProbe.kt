package io.embrace.android.embracesdk.internal.vitals

import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import androidx.annotation.WorkerThread
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode

/**
 * Detects blocks on [looper] by measuring how late a self-rescheduling sync message runs.
 */
internal class MainThreadProbe(
    looper: Looper,
    private val blockThresholdMs: Long,
    private val paceMs: Long = DEFAULT_PACE_MS,
    private val onBlock: BlockCallback,
    private val errorHandler: ErrorHandler,
) : Handler(looper) {

    /**
     * Receives each block on the probe's looper.
     */
    fun interface BlockCallback {

        /**
         * Called with the uptime at which a block ended and how long it lasted.
         */
        fun onBlock(endUptimeMs: Long, durationMs: Long)
    }

    @Volatile
    private var active = false

    @Volatile
    private var generation = 0

    @Volatile
    private var dueMs = 0L

    /**
     * Schedules the first probe message now unless the probe is already running.
     */
    @WorkerThread
    fun start() {
        if (active) {
            return
        }
        val gen = generation + 1
        val nowMs = SystemClock.uptimeMillis()
        generation = gen
        dueMs = nowMs
        active = true
        // sendMessage checks the time as well, so we omit one clock read by sendMessageAtTime
        sendMessageAtTime(obtainMessage(WHAT, gen, 0), nowMs)
    }

    /**
     * Stops the probe and removes its queued message.
     */
    @WorkerThread
    fun stop() {
        active = false
        removeMessages(WHAT)
    }

    /**
     * Returns true while the probe is running and its message is at least the block threshold past due.
     */
    @WorkerThread
    fun isOverdue(nowUptimeMs: Long): Boolean = active && nowUptimeMs - dueMs >= blockThresholdMs

    override fun dispatchMessage(msg: Message) {
        errorHandler.guardSdkCode(SdkErrorType.UiCallbackFail) { super.dispatchMessage(msg) }
    }

    override fun handleMessage(msg: Message) {
        val gen = msg.arg1
        if (!active || gen != generation) {
            return
        }
        val nowMs = SystemClock.uptimeMillis()
        val lateMs = nowMs - dueMs
        try {
            // checked only once a run is late, so the native debugger calls stay off the on-time path
            if (lateMs >= blockThresholdMs && !isDebuggerAttached()) {
                onBlock.onBlock(nowMs, lateMs)
            }
        } finally {
            val due = nowMs + paceMs
            dueMs = due
            sendMessageAtTime(obtainMessage(WHAT, gen, 0), due)
        }
    }

    // A thread paused by a debugger isn't blocked by the app.
    private fun isDebuggerAttached(): Boolean = Debug.isDebuggerConnected() || Debug.waitingForDebugger()

    companion object {

        /**
         * The interval between probe runs on an idle looper.
         */
        const val DEFAULT_PACE_MS = 16L

        private const val WHAT = 1
    }
}
