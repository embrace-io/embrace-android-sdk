package io.embrace.android.embracesdk.internal.instrumentation.thread.blockage

import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper

/**
 * Service which detects when the application is not responding.
 */
interface ThreadBlockageService :
    SessionPartChangeListener,
    OtelPayloadMapper {

    /**
     * Initializes capture of ANRs
     */
    fun startCapture()

    /**
     * Stops capture after the JVM crash identified by [crashId].
     */
    fun handleCrash(crashId: String)

    /**
     * Called when the app enters the foreground.
     */
    fun onForeground()

    /**
     * Called when the app enters the background.
     */
    fun onBackground()

    /**
     * Test hook not intended for production use. This simulates the target thread responding to messages
     * again (and therefore ending an ANR).
     */
    fun simulateTargetThreadResponse()
}
