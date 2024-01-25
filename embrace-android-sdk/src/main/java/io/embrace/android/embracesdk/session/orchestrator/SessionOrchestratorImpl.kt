package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.session.BackgroundActivityService
import io.embrace.android.embracesdk.session.ConfigGate
import io.embrace.android.embracesdk.session.SessionService
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService

internal class SessionOrchestratorImpl(
    private val processStateService: ProcessStateService,
    private val sessionService: SessionService,
    backgroundActivityServiceImpl: BackgroundActivityService?,
    private val clock: Clock,
    private val configService: ConfigService,
    private val sessionIdTracker: SessionIdTracker,
    private val lock: Any,
    private val boundaryDelegate: OrchestratorBoundaryDelegate
) : SessionOrchestrator {

    companion object {

        /**
         * The minimum threshold for how long a session must last. This prevents unintentional
         * instrumentation from creating new sessions in a hot loop & therefore spamming
         * our servers.
         */
        private const val MIN_SESSION_MS = 5000L
    }

    private val backgroundActivityGate = ConfigGate(backgroundActivityServiceImpl) {
        configService.isBackgroundActivityCaptureEnabled()
    }
    private val backgroundActivityService: BackgroundActivityService?
        get() = backgroundActivityGate.getService()

    init {
        processStateService.addListener(this)
        configService.addListener(backgroundActivityGate)

        createInitialEnvelope()
    }

    private fun createInitialEnvelope() {
        synchronized(lock) {
            val inBackground = processStateService.isInBackground
            val sessionId = if (inBackground) {
                backgroundActivityService?.startBackgroundActivityWithState(true, clock.now())
            } else {
                // If the app goes to foreground before the SDK finishes its startup,
                // the session service will not be registered to the activity listener and will not
                // start the cold session.
                // If so, force a cold session start.
                sessionService.startSessionWithState(true, clock.now())
            }
            sessionIdTracker.setActiveSessionId(
                sessionId = sessionId,
                isSession = !inBackground
            )
        }
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        synchronized(lock) {
            backgroundActivityService?.endBackgroundActivityWithState(timestamp)
            boundaryDelegate.prepareForNewEnvelope()
            val sessionId = sessionService.startSessionWithState(coldStart, timestamp)
            sessionIdTracker.setActiveSessionId(sessionId, true)
        }
    }

    override fun onBackground(timestamp: Long) {
        synchronized(lock) {
            sessionService.endSessionWithState(timestamp)
            boundaryDelegate.prepareForNewEnvelope()
            val sessionId = backgroundActivityService?.startBackgroundActivityWithState(false, timestamp)
            sessionIdTracker.setActiveSessionId(sessionId, false)
        }
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        synchronized(lock) {
            if (processStateService.isInBackground) {
                return
            }
            if (configService.sessionBehavior.isSessionControlEnabled()) {
                return
            }
            val startTime = sessionService.activeSession?.startTime ?: 0
            if ((clock.now() - startTime) < MIN_SESSION_MS) {
                return
            }

            sessionService.endSessionWithManual()
            boundaryDelegate.prepareForNewEnvelope(clearUserInfo)
            val sessionId = sessionService.startSessionWithManual()
            sessionIdTracker.setActiveSessionId(sessionId, true)
        }
    }

    override fun endSessionWithCrash(crashId: String) {
        synchronized(lock) {
            sessionService.endSessionWithCrash(crashId)
            backgroundActivityService?.endBackgroundActivityWithCrash(crashId)
        }
    }
}
