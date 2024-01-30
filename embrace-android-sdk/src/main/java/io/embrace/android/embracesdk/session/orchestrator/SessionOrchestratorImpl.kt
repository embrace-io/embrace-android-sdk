package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.session.ConfigGate
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.session.message.BackgroundActivityService
import io.embrace.android.embracesdk.session.message.SessionService

internal class SessionOrchestratorImpl(
    private val processStateService: ProcessStateService,
    private val sessionService: SessionService,
    backgroundActivityServiceImpl: BackgroundActivityService?,
    private val clock: Clock,
    private val configService: ConfigService,
    private val sessionIdTracker: SessionIdTracker,
    private val boundaryDelegate: OrchestratorBoundaryDelegate,
    private val periodicSessionCacher: PeriodicSessionCacher,
    private val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : SessionOrchestrator {

    private val lock = Any()

    /**
     * The currently active session.
     */
    private var activeSession: Session? = null
    private var state = when {
        processStateService.isInBackground -> ProcessState.BACKGROUND
        else -> ProcessState.FOREGROUND
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
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.INITIAL,
            timestamp = timestamp,
            newSessionAction = {
                if (state == ProcessState.BACKGROUND) {
                    backgroundActivityService?.startBackgroundActivityWithState(timestamp, true)
                } else {
                    sessionService.startSessionWithState(timestamp, true)
                }
            }
        )
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        transitionState(
            transitionType = TransitionType.ON_FOREGROUND,
            timestamp = timestamp,
            oldSessionAction = { initial: Session ->
                backgroundActivityService?.endBackgroundActivityWithState(initial, timestamp)
            },
            newSessionAction = {
                sessionService.startSessionWithState(timestamp, coldStart)
            },
            earlyTerminationCondition = {
                return@transitionState shouldRunOnForeground(state)
            }
        )
    }

    override fun onBackground(timestamp: Long) {
        transitionState(
            transitionType = TransitionType.ON_BACKGROUND,
            timestamp = timestamp,
            oldSessionAction = { initial: Session ->
                sessionService.endSessionWithState(initial, timestamp)
            },
            newSessionAction = {
                backgroundActivityService?.startBackgroundActivityWithState(timestamp, false)
            },
            earlyTerminationCondition = {
                return@transitionState shouldRunOnBackground(state)
            }
        )
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.END_MANUAL,
            timestamp = timestamp,
            clearUserInfo = clearUserInfo,
            oldSessionAction = { initial: Session ->
                sessionService.endSessionWithManual(initial, timestamp)
            },
            newSessionAction = {
                sessionService.startSessionWithManual(timestamp)
            },
            earlyTerminationCondition = {
                return@transitionState shouldEndManualSession(
                    configService,
                    clock,
                    activeSession,
                    state
                )
            }
        )
    }

    override fun endSessionWithCrash(crashId: String) {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.CRASH,
            timestamp = timestamp,
            oldSessionAction = { initial: Session ->
                if (processStateService.isInBackground) {
                    backgroundActivityService?.endBackgroundActivityWithCrash(
                        initial,
                        timestamp,
                        crashId
                    )
                } else {
                    sessionService.endSessionWithCrash(initial, timestamp, crashId)
                }
            }
        )
    }

    override fun reportBackgroundActivityStateChange() {
        if (state == ProcessState.BACKGROUND) {
            val initial = activeSession ?: return
            scheduleBackgroundActivitySave(initial)
        }
    }

    /**
     * This function is responsible for transitioning state from one session to another. This can
     * be summarised in 3 steps:
     *
     * 1. End the current session or background activity.
     * 2. Clean up any previous session state.
     * 3. Start the next session or background activity.
     *
     * @param transitionType    The transition type
     * @param timestamp         The timestamp of the transition.
     * @param oldSessionAction  The action that ends the old session or background activity (if any).
     * The initial session object (if any) is passed as a parameter to allow building a full payload.
     * @param newSessionAction  The action that starts the new session or background activity (if any).
     * If a new session is created this must return a session object containing the initial state.
     * @param clearUserInfo     Whether to clear user info when ending the session. Defaults to false
     */
    private fun transitionState(
        transitionType: TransitionType,
        timestamp: Long,
        oldSessionAction: ((initial: Session) -> Unit)? = null,
        newSessionAction: (() -> Session?)? = null,
        earlyTerminationCondition: () -> Boolean = { false },
        clearUserInfo: Boolean = false,
    ) {
        // supplied business logic says that we can't perform a transition yet.
        // exit early & retain the current state instead.
        if (earlyTerminationCondition()) {
            return
        }
        synchronized(lock) {
            if (earlyTerminationCondition()) { // optimization: check again after acquiring lock
                return
            }

            // first, end the current session or background activity, if either exist.
            val initial = activeSession
            if (initial != null) {
                oldSessionAction?.invoke(initial)
            }

            // next, clean up any previous session state
            boundaryDelegate.prepareForNewEnvelope(timestamp, clearUserInfo)

            // start the next session or background activity
            val newState = newSessionAction?.invoke()
            activeSession = newState
            val sessionId = newState?.sessionId
            val endProcessState = transitionType.endState(state)
            val inForeground = endProcessState == ProcessState.FOREGROUND
            sessionIdTracker.setActiveSessionId(sessionId, inForeground)

            // disable any previous periodic caching.
            when (endProcessState) {
                ProcessState.FOREGROUND -> periodicBackgroundActivityCacher.stop()
                ProcessState.BACKGROUND -> periodicSessionCacher.stop()
            }

            // initiate periodic caching of the payload if required
            if (transitionType != TransitionType.CRASH && newState != null) {
                initiatePeriodicCaching(endProcessState, newState)
            }

            // update the current state
            state = endProcessState

            // log the state change
            logSessionStateChange(
                sessionId,
                timestamp,
                !inForeground,
                transitionType.name
            )
        }
    }

    private fun initiatePeriodicCaching(
        endProcessState: ProcessState,
        newState: Session
    ) {
        when (endProcessState) {
            ProcessState.FOREGROUND -> {
                periodicSessionCacher.start {
                    synchronized(lock) {
                        sessionService.snapshotSession(newState, clock.now())
                    }
                }
            }
            ProcessState.BACKGROUND -> scheduleBackgroundActivitySave(newState)
        }
    }

    private fun scheduleBackgroundActivitySave(initial: Session) {
        periodicBackgroundActivityCacher.scheduleSave {
            synchronized(lock) {
                backgroundActivityService?.snapshotBackgroundActivity(initial, clock.now())
            }
        }
    }

    private fun logSessionStateChange(
        sessionId: String?,
        timestamp: Long,
        inBackground: Boolean,
        stateChange: String,
        logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
    ) {
        val type = when {
            inBackground -> "background"
            else -> "session"
        }
        logger.logDebug("New session created: ID=$sessionId, timestamp=$timestamp, type=$type, state_change=$stateChange")
    }
}
