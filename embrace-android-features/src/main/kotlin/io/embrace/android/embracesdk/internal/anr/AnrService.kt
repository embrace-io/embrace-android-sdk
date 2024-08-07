package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.arch.DataCaptureService
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.AnrInterval
import java.io.Closeable

/**
 * Service which detects when the application is not responding.
 */
public interface AnrService :
    DataCaptureService<List<AnrInterval>>,
    CrashTeardownHandler,
    Closeable {

    /**
     * Finishes initialization of the AnrService so that it can react appropriately to
     * lifecycle changes and capture the correct data according to the config. This is necessary
     * as the service can be initialized before the rest of the SDK.
     *
     * @param configService        the configService
     */
    public fun finishInitialization(
        configService: ConfigService
    )

    /**
     * Adds a listener which is invoked when the thread becomes blocked/unblocked.
     */
    public fun addBlockedThreadListener(listener: BlockedThreadListener)
}
