package io.embrace.android.embracesdk.internal.anr

import io.embrace.android.embracesdk.internal.arch.DataCaptureService
import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.payload.AnrInterval
import java.io.Closeable

/**
 * Service which detects when the application is not responding.
 */
interface AnrService :
    DataCaptureService<List<AnrInterval>>,
    CrashTeardownHandler,
    Closeable {

    /**
     * Initializes capture of ANRs
     */
    fun startAnrCapture()

    /**
     * Adds a listener which is invoked when the thread becomes blocked/unblocked.
     */
    fun addBlockedThreadListener(listener: BlockedThreadListener)
}
