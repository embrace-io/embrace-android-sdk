package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import java.util.concurrent.atomic.AtomicBoolean

internal class SdkCallChecker(
    private val logger: EmbLogger,
    private val telemetryService: TelemetryService?,
) {

    /**
     * Whether the Embrace SDK has been started yet.
     */
    val started = AtomicBoolean(false)

    /**
     * Checks if the SDK is started and logs the public API usage.
     *
     * Every public API usage should go through this method, except the ones that are called too often and may cause a performance hit.
     * For instance, get_current_session_id and get_trace_id_header go directly through checkSdkStarted.
     */
    fun check(action: String): Boolean {
        val isStarted = started.get()
        if (!isStarted) {
            logger.logSdkNotInitialized(action)
        }
        telemetryService?.onPublicApiCalled(action)
        return isStarted
    }
}
