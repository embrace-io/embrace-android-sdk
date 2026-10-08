package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.logging.InternalLogger
import java.util.concurrent.atomic.AtomicBoolean

internal class SdkCallChecker(
    private val logger: InternalLogger,
) {

    /**
     * Whether the Embrace SDK has been started yet.
     */
    val started = AtomicBoolean(false)

    /**
     * Checks if the SDK is started, logging an error if it is not.
     */
    fun check(action: String, outputErrorMessage: Boolean = true): Boolean {
        val isStarted = started.get()
        if (!isStarted && outputErrorMessage) {
            logger.logSdkNotInitialized(action)
        }
        return isStarted
    }
}
