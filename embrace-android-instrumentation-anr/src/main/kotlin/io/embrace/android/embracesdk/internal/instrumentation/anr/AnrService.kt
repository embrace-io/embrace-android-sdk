package io.embrace.android.embracesdk.internal.instrumentation.anr

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.session.MemoryCleanerListener

/**
 * Service which detects when the application is not responding.
 */
interface AnrService :
    MemoryCleanerListener,
    CrashTeardownHandler,
    AppStateListener,
    OtelPayloadMapper {

    /**
     * Initializes capture of ANRs
     */
    fun startAnrCapture()
}
