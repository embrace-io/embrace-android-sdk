package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.arch.SessionType
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.session.message.PayloadFactory

internal class SessionOrchestratorImpl(
    processStateService: ProcessStateService,
    private val payloadFactory: PayloadFactory,
    private val clock: Clock,
    private val configService: ConfigService,
    private val sessionIdTracker: SessionIdTracker,
    private val boundaryDelegate: OrchestratorBoundaryDelegate,
    private val deliveryService: DeliveryService,
    private val periodicSessionCacher: PeriodicSessionCacher,
    private val periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher,
    private val dataCaptureOrchestrator: DataCaptureOrchestrator,
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

    init {
        processStateService.addListener(this)
        Systrace.traceSynchronous("start-first-session") { createInitialSession() }
    }

    private fun createInitialSession() {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.INITIAL,
            timestamp = timestamp,
            newSessionAction = {
                payloadFactory.startPayloadWithState(state, timestamp, true)
            }
        )
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        transitionState(
            transitionType = TransitionType.ON_FOREGROUND,
            timestamp = timestamp,
            oldSessionAction = { initial: Session ->
                payloadFactory.endPayloadWithState(ProcessState.BACKGROUND, timestamp, initial)
            },
            newSessionAction = {
                payloadFactory.startPayloadWithState(ProcessState.FOREGROUND, timestamp, coldStart)
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
                payloadFactory.endPayloadWithState(ProcessState.FOREGROUND, timestamp, initial)
            },
            newSessionAction = {
                payloadFactory.startPayloadWithState(ProcessState.BACKGROUND, timestamp, false)
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
                payloadFactory.endSessionWithManual(timestamp, initial)
            },
            newSessionAction = {
                payloadFactory.startSessionWithManual(timestamp)
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
                payloadFactory.endPayloadWithCrash(state, timestamp, initial, crashId)
            }
        )
    }

    override fun reportBackgroundActivityStateChange() {
        if (state == ProcessState.BACKGROUND) {
            val initial = activeSession ?: return
            scheduleBackgroundActivitySave(ProcessState.BACKGROUND, initial)
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
        oldSessionAction: ((initial: Session) -> SessionMessage?)? = null,
        newSessionAction: (Provider<Session?>)? = null,
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

            // first, disable any previous periodic caching so the job doesn't overwrite the to-be saved session
            val endProcessState = transitionType.endState(state)
            val inForeground = endProcessState == ProcessState.FOREGROUND
            when (endProcessState) {
                ProcessState.FOREGROUND -> periodicBackgroundActivityCacher.stop()
                ProcessState.BACKGROUND -> periodicSessionCacher.stop()
            }

            // second, end the current session or background activity, if either exist.
            val initial = activeSession
            if (initial != null) {
                val endMessage = oldSessionAction?.invoke(initial)
                processEndMessage(endMessage, transitionType)
            }

            // third, clean up any previous session state
            boundaryDelegate.prepareForNewSession(timestamp, clearUserInfo)

            // now, we can start the next session or background activity
            val newState = newSessionAction?.invoke()
            activeSession = newState
            val sessionId = newState?.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, inForeground)

            // initiate periodic caching of the payload if required
            if (transitionType != TransitionType.CRASH && newState != null) {
                initiatePeriodicCaching(endProcessState, newState)
            }

            // update the current state
            state = endProcessState

            // update data capture orchestrator
            val sessionType = when (endProcessState) {
                ProcessState.FOREGROUND -> SessionType.FOREGROUND
                ProcessState.BACKGROUND -> SessionType.BACKGROUND
            }
            dataCaptureOrchestrator.onSessionTypeChange(sessionType)

            // log the state change
            logSessionStateChange(
                sessionId,
                timestamp,
                !inForeground,
                transitionType.name
            )

            // et voila! a new session is born
        }
    }

    private fun processEndMessage(endMessage: SessionMessage?, transitionType: TransitionType) {
        endMessage?.let {
            val type = when (transitionType) {
                TransitionType.CRASH -> SessionSnapshotType.JVM_CRASH
                else -> SessionSnapshotType.NORMAL_END
            }
            deliveryService.sendSession(it, type)
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
                        payloadFactory.snapshotPayload(endProcessState, clock.now(), newState)?.apply {
                            deliveryService.sendSession(this, SessionSnapshotType.PERIODIC_CACHE)
                        }
                    }
                }
            }

            ProcessState.BACKGROUND -> scheduleBackgroundActivitySave(endProcessState, newState)
        }
    }

    private fun scheduleBackgroundActivitySave(endProcessState: ProcessState, initial: Session) {
        periodicBackgroundActivityCacher.scheduleSave {
            synchronized(lock) {
                payloadFactory.snapshotPayload(endProcessState, clock.now(), initial)?.apply {
                    deliveryService.sendSession(this, SessionSnapshotType.PERIODIC_CACHE)
                }
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
