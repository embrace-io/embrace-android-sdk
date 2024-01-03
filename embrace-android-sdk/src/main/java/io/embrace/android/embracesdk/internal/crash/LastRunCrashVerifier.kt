package io.embrace.android.embracesdk.internal.crash

import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Verifies if the last run crashed.
 * This is done by checking if the crash marker file exists.
 */
internal class LastRunCrashVerifier(private val crashFileMarker: CrashFileMarker) {

    private var didLastRunCrashFuture: Future<Boolean>? = null
    private var didLastRunCrash: Boolean? = null

    /**
     * Returns true if the app crashed in the last run, false otherwise.
     */
    fun didLastRunCrash(): Boolean {
        return didLastRunCrash ?: didLastRunCrashFuture?.let { future ->
            try {
                future.get(2, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                InternalStaticEmbraceLogger.logError("[Embrace] didLastRunCrash: error while getting the result", e)
                null
            }
        } ?: readAndCleanMarker()
    }

    /**
     * Reads and clean the last run crash marker in a background thread.
     * This method is called when the SDK is started.
     */
    fun readAndCleanMarkerAsync(executorService: ExecutorService) {
        if (didLastRunCrash == null) {
            this.didLastRunCrashFuture = executorService.submit<Boolean> {
                readAndCleanMarker()
            }
        }
    }

    /**
     * Reads and clean the last run crash marker.
     * @return true if the app crashed in the last run, false otherwise
     */
    private fun readAndCleanMarker(): Boolean {
        return crashFileMarker.getAndCleanMarker().also {
            this.didLastRunCrash = it
        }
    }
}
