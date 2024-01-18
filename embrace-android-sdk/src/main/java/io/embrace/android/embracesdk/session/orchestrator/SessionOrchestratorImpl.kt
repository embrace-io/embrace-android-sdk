package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService

internal class SessionOrchestratorImpl(
    private val processStateService: ProcessStateService,
    private val sessionService: SessionService,
    private val backgroundActivityService: BackgroundActivityService?,
    clock: Clock
) : SessionOrchestrator {

    init {
        processStateService.addListener(this)

        if (processStateService.isInBackground) {
            backgroundActivityService?.startBackgroundActivityWithState(true, clock.now())
        } else {
            // If the app goes to foreground before the SDK finishes its startup,
            // the session service will not be registered to the activity listener and will not
            // start the cold session.
            // If so, force a cold session start.
            sessionService.startSessionWithState(true, clock.now())
        }
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        backgroundActivityService?.endBackgroundActivityWithState(timestamp)
        sessionService.startSessionWithState(coldStart, timestamp)
    }

    override fun onBackground(timestamp: Long) {
        sessionService.endSessionWithState(timestamp)
        backgroundActivityService?.startBackgroundActivityWithState(false, timestamp)
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        if (processStateService.isInBackground) {
            return
        }
        sessionService.endSessionWithManual(clearUserInfo)
    }
}
