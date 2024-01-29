package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Session
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

    /**
     * The currently active session.
     */
    private var activeSession: Session? = null
    private var inBackground = processStateService.isInBackground

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

            val session = if (inBackground) {
                backgroundActivityService?.startBackgroundActivityWithState(timestamp, true)
            } else {
                sessionService.startSessionWithState(timestamp, true)
            }
            activeSession = session
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
            if (!inBackground) {
                return
            }
            inBackground = false
            val initial = activeSession
            if (initial != null) {
                backgroundActivityService?.endBackgroundActivityWithState(initial, timestamp)
            }
            boundaryDelegate.prepareForNewEnvelope(timestamp)
            val session = sessionService.startSessionWithState(timestamp, coldStart)
            activeSession = session
            val sessionId = session.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, true)
            logSessionStateChange(sessionId, timestamp, false, "onForeground")
        }
    }

    override fun onBackground(timestamp: Long) {
        synchronized(lock) {
            if (inBackground) {
                return
            }
            inBackground = true
            val initial = activeSession
            if (initial != null) {
                sessionService.endSessionWithState(initial, timestamp)
            }
            activeSession = null
            boundaryDelegate.prepareForNewEnvelope(timestamp)
            val session = backgroundActivityService?.startBackgroundActivityWithState(
                timestamp,
                false
            )
            activeSession = session
            val sessionId = session?.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, false)
            logSessionStateChange(sessionId, timestamp, true, "onBackground")
        }
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        synchronized(lock) {
            val initial = activeSession ?: return
            if (inBackground) {
                logger.logWarning("Cannot manually end session while in background.")
                return
            }
            if (configService.sessionBehavior.isSessionControlEnabled()) {
                logger.logWarning("Cannot manually end session while session control is enabled.")
                return
            }
            val startTime = initial.startTime
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
            sessionService.endSessionWithManual(initial, timestamp)
            activeSession = null
            boundaryDelegate.prepareForNewEnvelope(timestamp, clearUserInfo)
            val session = sessionService.startSessionWithManual(timestamp)
            activeSession = session
            val sessionId = session.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, true)
            logSessionStateChange(sessionId, timestamp, false, "endManual")
        }
    }

    override fun endSessionWithCrash(crashId: String) {
        synchronized(lock) {
            val initial = activeSession ?: return
            val timestamp = clock.now()

            if (inBackground) {
                backgroundActivityService?.endBackgroundActivityWithCrash(
                    initial,
                    timestamp,
                    crashId
                )
            } else {
                sessionService.endSessionWithCrash(initial, timestamp, crashId)
            }
            activeSession = null
            logger.logDebug("Session ended with crash")
        }
    }

    override fun reportBackgroundActivityStateChange() {
        if (inBackground) {
            val initial = activeSession ?: return
            backgroundActivityService?.saveBackgroundActivitySnapshot(initial)
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
