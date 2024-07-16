package io.embrace.android.embracesdk.internal.anr.detection

import io.embrace.android.embracesdk.internal.anr.BlockedThreadListener
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

internal class UnbalancedCallDetector(
    private val logger: EmbLogger
) : BlockedThreadListener {

    @Volatile
    private var blocked: Boolean = false

    @Volatile
    private var lastTimestamp: Long = 0

    override fun onThreadBlocked(thread: Thread, timestamp: Long) {
        checkUnbalancedCall("onThreadBlocked()", false)
        blocked = true
        checkTimeTravel("onThreadBlocked()", timestamp)
    }

    override fun onThreadBlockedInterval(thread: Thread, timestamp: Long) {
        checkUnbalancedCall("onThreadBlockedInterval()", true)
        checkTimeTravel("onThreadBlockedInterval()", timestamp)
    }

    override fun onThreadUnblocked(thread: Thread, timestamp: Long) {
        checkUnbalancedCall("onThreadUnblocked()", true)
        blocked = false
        checkTimeTravel("onThreadUnblocked()", timestamp)
    }

    private fun checkTimeTravel(name: String, timestamp: Long) {
        if (lastTimestamp > timestamp) {
            val msg = "Time travel in $name. $lastTimestamp to $timestamp"
            logger.logWarning(msg)
            logger.trackInternalError(InternalErrorType.TIME_TRAVEL, IllegalStateException("Time Travel"))
        }
        lastTimestamp = timestamp
    }

    private fun checkUnbalancedCall(name: String, expected: Boolean) {
        if (blocked != expected) {
            val threadName = Thread.currentThread().name
            val msg = "Unbalanced call to $name in ANR detection. Thread=$threadName"
            logger.logWarning(msg)
            logger.trackInternalError(InternalErrorType.UNBALANCED_CALL, IllegalStateException("Unbalanced call"))
        }
    }
}
