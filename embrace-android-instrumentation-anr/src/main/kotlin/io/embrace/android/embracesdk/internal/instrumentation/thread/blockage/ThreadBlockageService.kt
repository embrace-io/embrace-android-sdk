package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

/**
 * Service which detects when the application is not responding.
 */
interface ThreadBlockageService :
    MemoryCleanerListener,
    CrashTeardownHandler,
    AppStateListener,
    OtelPayloadMapper {

    /**
     * Initializes capture of ANRs
     */
    fun startCapture()

    /**
     * Test hook not intended for production use. This simulates the target thread responding to messages
     * again (and therefore ending an ANR).
     */
    fun simulateTargetThreadResponse()
}
