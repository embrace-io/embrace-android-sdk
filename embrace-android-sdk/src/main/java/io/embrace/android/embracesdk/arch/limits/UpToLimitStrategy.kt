package io.embrace.android.embracesdk.arch.limits

import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Allows capturing data up until a limit, then stops capturing.
 */
internal class UpToLimitStrategy(
    private val limitProvider: Provider<Int>,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : LimitStrategy {

    private var lock = Any()
    private var count = 0

    override fun shouldCapture(): Boolean {
        synchronized(lock) {
            if (count >= limitProvider()) {
                logger.logWarning("Data capture limit reached.")
                return false
            }
            count++
            return true
        }
    }

    override fun resetDataCaptureLimits() {
        synchronized(lock) {
            count = 0
        }
    }
}
