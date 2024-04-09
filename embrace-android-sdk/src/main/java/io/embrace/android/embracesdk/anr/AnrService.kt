package io.embrace.android.embracesdk.anr

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.arch.DataCaptureServiceOtelConverter
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.payload.AnrInterval
import java.io.Closeable

/**
 * Service which detects when the application is not responding.
 */
internal interface AnrService :
    DataCaptureService<List<AnrInterval>>,
    Closeable,
    DataCaptureServiceOtelConverter {

    /**
     * Forces ANR tracking stop by closing the monitoring thread when a crash is
     * handled by the [EmbraceCrashService].
     */
    fun forceAnrTrackingStopOnCrash()

    /**
     * Finishes initialization of the AnrService so that it can react appropriately to
     * lifecycle changes and capture the correct data according to the config. This is necessary
     * as the service can be initialized before the rest of the SDK.
     *
     * @param configService        the configService
     */
    fun finishInitialization(
        configService: ConfigService
    )

    /**
     * Adds a listener which is invoked when the thread becomes blocked/unblocked.
     */
    fun addBlockedThreadListener(listener: BlockedThreadListener)
}
