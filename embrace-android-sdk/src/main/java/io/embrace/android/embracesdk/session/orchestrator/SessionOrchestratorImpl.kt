package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
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
    private val boundaryDelegate: OrchestratorBoundaryDelegate,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
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
            val timestamp = clock.now()
            boundaryDelegate.prepareForNewEnvelope(timestamp)

            val inBackground = processStateService.isInBackground
            val session = if (inBackground) {
                backgroundActivityService?.startBackgroundActivityWithState(timestamp, true)
            } else {
                sessionService.startSessionWithState(timestamp, true)
            }
            val sessionId = session?.sessionId
            sessionIdTracker.setActiveSessionId(
                sessionId = sessionId,
                isSession = !inBackground
            )
            logSessionStateChange(sessionId, timestamp, inBackground, "initial")
        }
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        synchronized(lock) {
            backgroundActivityService?.endBackgroundActivityWithState(timestamp)
            boundaryDelegate.prepareForNewEnvelope(timestamp)
            val session = sessionService.startSessionWithState(timestamp, coldStart)
            val sessionId = session.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, true)
            logSessionStateChange(sessionId, timestamp, false, "onForeground")
        }
    }

    override fun onBackground(timestamp: Long) {
        synchronized(lock) {
            sessionService.endSessionWithState(timestamp)
            boundaryDelegate.prepareForNewEnvelope(timestamp)
            val session = backgroundActivityService?.startBackgroundActivityWithState(
                timestamp,
                false
            )
            val sessionId = session?.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, false)
            logSessionStateChange(sessionId, timestamp, true, "onBackground")
        }
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        synchronized(lock) {
            if (processStateService.isInBackground) {
                logger.logWarning("Cannot manually end session while in background.")
                return
            }
            if (configService.sessionBehavior.isSessionControlEnabled()) {
                logger.logWarning("Cannot manually end session while session control is enabled.")
                return
            }
            val startTime = sessionService.activeSession?.startTime ?: 0
            val delta = clock.now() - startTime
            if (delta < MIN_SESSION_MS) {
                logger.logWarning(
                    "Cannot manually end session while session is <5s long." +
                        "This protects against instrumentation unintentionally creating too" +
                        "many sessions"
                )
                return
            }

            val timestamp = clock.now()
            sessionService.endSessionWithManual(timestamp)
            boundaryDelegate.prepareForNewEnvelope(timestamp, clearUserInfo)
            val session = sessionService.startSessionWithManual(timestamp)
            val sessionId = session.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, true)
            logSessionStateChange(sessionId, timestamp, false, "endManual")
        }
    }

    override fun endSessionWithCrash(crashId: String) {
        synchronized(lock) {
            val timestamp = clock.now()

            if (processStateService.isInBackground) {
                backgroundActivityService?.endBackgroundActivityWithCrash(timestamp, crashId)
            } else {
                sessionService.endSessionWithCrash(timestamp, crashId)
            }
            logger.logDebug("Session ended with crash")
        }
    }

    override fun reportBackgroundActivityStateChange() {
        if (processStateService.isInBackground) {
            backgroundActivityService?.saveBackgroundActivitySnapshot()
        }
    }

    private fun logSessionStateChange(
        sessionId: String?,
        timestamp: Long,
        inBackground: Boolean,
        stateChange: String
    ) {
        val type = when {
            inBackground -> "background"
            else -> "session"
        }
        logger.logDebug("New session created: ID=$sessionId, timestamp=$timestamp, type=$type, state_change=$stateChange")
    }
}
