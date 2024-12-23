package io.embrace.android.embracesdk.internal.crash

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Verifies if the last run crashed.
 * This is done by checking if the crash marker file exists.
 */
class LastRunCrashVerifier(
    private val crashFileMarker: CrashFileMarker,
) {

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
                null
            }
        } ?: readAndCleanMarker()
    }

    /**
     * Reads and clean the last run crash marker in a background thread.
     * This method is called when the SDK is started.
     */
    fun readAndCleanMarkerAsync(backgroundWorker: BackgroundWorker) {
        if (didLastRunCrash == null) {
            this.didLastRunCrashFuture = backgroundWorker.submit<Boolean> {
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
