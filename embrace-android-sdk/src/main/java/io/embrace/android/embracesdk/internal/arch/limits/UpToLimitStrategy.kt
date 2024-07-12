package io.embrace.android.embracesdk.internal.arch.limits

import io.embrace.android.embracesdk.annotation.InternalApi
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Allows capturing data up until a limit, then stops capturing.
 */
@InternalApi
public class UpToLimitStrategy(
    private val limitProvider: Provider<Int>,
) : LimitStrategy {

    private var lock = Any()
    private var count = 0

    override fun shouldCapture(): Boolean {
        synchronized(lock) {
            if (count >= limitProvider()) {
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
