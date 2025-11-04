package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.instrumentation.anr.payload.AnrInterval
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener

/**
 * Service which detects when the application is not responding.
 */
interface AnrService :
    MemoryCleanerListener,
    CrashTeardownHandler,
    ProcessStateListener,
    BlockedThreadListener {

    /**
     * Returns a representation of all the data that has already been captured so far.
     *
     * This does NOT mean that implementations should go capture data - they should just return
     * what has already been captured, if anything.
     */
    fun getCapturedData(): List<AnrInterval>

    /**
     * Initializes capture of ANRs
     */
    fun startAnrCapture()

    /**
     * Adds a listener which is invoked when the thread becomes blocked/unblocked.
     */
    fun addBlockedThreadListener(listener: BlockedThreadListener)
}
