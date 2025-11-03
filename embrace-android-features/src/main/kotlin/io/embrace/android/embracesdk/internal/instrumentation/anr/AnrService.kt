package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.DataCaptureService
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval

/**
 * Service which detects when the application is not responding.
 */
interface AnrService : DataCaptureService<List<AnrInterval>> {

    /**
     * Initializes capture of ANRs
     */
    fun startAnrCapture()

    /**
     * Adds a listener which is invoked when the thread becomes blocked/unblocked.
     */
    fun addBlockedThreadListener(listener: BlockedThreadListener)
}
