package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.internal.arch.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper

/**
 * Service which detects when the application is not responding.
 */
interface ThreadBlockageService :
    SessionPartChangeListener,
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

    /**
     * Registers [listener] to receive the thread blockages that this service detects. Registering the
     * same listener twice has no effect, and a listener may be added at any point — it receives every
     * blockage reported after it was added.
     *
     * Callbacks are delivered on the watchdog thread, and the [ThreadBlockage] they carry is reused, so a
     * listener that retains one must retain [ThreadBlockage.copy] instead.
     */
    fun addListener(listener: ThreadBlockageListener)

    /**
     * Stops [listener] receiving thread blockages.
     */
    fun removeListener(listener: ThreadBlockageListener)
}
