package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.capture.crash.CrashTeardownHandler
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateListener

/**
 * Orchestrates the session and background activities in response to state changes and manual
 * requests to end sessions.
 */
public interface SessionOrchestrator : ProcessStateListener, CrashTeardownHandler {

    /**
     * Ends the current session (if any) manually. If [clearUserInfo] is true,
     * the user info will be cleared. This has no effect on background activities.
     */
    public fun endSessionWithManual(clearUserInfo: Boolean)

    /**
     * Reports a change that means we should schedule snapshotting & writing to disk of the
     * current snapshot. This function is kept for legacy reasons and will eventually be removed.
     */
    public fun reportBackgroundActivityStateChange()
}
