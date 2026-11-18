package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Verifies if the last run crashed.
 * This is done by checking if the crash marker file exists.
 */
class LastRunCrashVerifier(
    private val crashFileMarker: CrashFileMarker,
    private val isLegacyTelemetryMigrated: () -> Boolean = { true },
) {

    @Volatile
    private var didLastRunCrashFuture: Future<Boolean>? = null

    @Volatile
    private var didLastRunCrash: Boolean? = null

    /**
     * Returns true if the app crashed in the last run, false otherwise. While a 9.x crash marker may still be moving into
     * place, the marker isn't read here and false is returned without caching it, so the async read still runs.
     */
    fun didLastRunCrash(): Boolean {
        return didLastRunCrash ?: didLastRunCrashFuture?.let { future ->
            try {
                future.get(2, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                null
            }
        } ?: readAndCleanMarkerIfMigrated()
    }

    private fun readAndCleanMarkerIfMigrated(): Boolean = isLegacyTelemetryMigrated() && readAndCleanMarker()

    /**
     * Reads and clean the last run crash marker in a background thread.
     * This method is called when the SDK is started.
     */
    fun readAndCleanMarkerAsync(
        backgroundWorker: BackgroundWorker,
        awaitLegacyTelemetryMigration: () -> Unit = {},
    ) {
        if (didLastRunCrash == null) {
            this.didLastRunCrashFuture = backgroundWorker.submit<Boolean> {
                awaitLegacyTelemetryMigration()
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
