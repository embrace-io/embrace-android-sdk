package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.SessionType
import io.embrace.android.embracesdk.internal.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.internal.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.opentelemetry.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.opentelemetry.embTerminated
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.utils.Provider

internal class SessionOrchestratorImpl(
    processStateService: ProcessStateService,
    private val payloadFactory: PayloadFactory,
    private val clock: Clock,
    private val configService: ConfigService,
    private val sessionIdTracker: SessionIdTracker,
    private val boundaryDelegate: OrchestratorBoundaryDelegate,
    private val payloadStore: PayloadStore?,
    private val payloadCachingService: PayloadCachingService?,
    private val dataCaptureOrchestrator: DataCaptureOrchestrator,
    private val sessionSpanWriter: SessionSpanWriter,
    private val sessionSpanAttrPopulator: SessionSpanAttrPopulator,
    private val logger: EmbLogger
) : SessionOrchestrator {

    private val lock = Any()

    /**
     * The currently active session.
     */
    private var activeSession: SessionZygote? = null
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
            oldSessionAction = { initial: SessionZygote ->
                payloadFactory.endPayloadWithState(ProcessState.BACKGROUND, timestamp, initial)
            },
            newSessionAction = {
                payloadFactory.startPayloadWithState(ProcessState.FOREGROUND, timestamp, coldStart)
            },
            earlyTerminationCondition = {
                return@transitionState shouldRunOnForeground(state, logger)
            }
        )
    }

    override fun onBackground(timestamp: Long) {
        transitionState(
            transitionType = TransitionType.ON_BACKGROUND,
            timestamp = timestamp,
            oldSessionAction = { initial: SessionZygote ->
                payloadFactory.endPayloadWithState(ProcessState.FOREGROUND, timestamp, initial)
            },
            newSessionAction = {
                payloadFactory.startPayloadWithState(ProcessState.BACKGROUND, timestamp, false)
            },
            earlyTerminationCondition = {
                return@transitionState shouldRunOnBackground(state, logger)
            }
        )
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.END_MANUAL,
            timestamp = timestamp,
            clearUserInfo = clearUserInfo,
            oldSessionAction = { initial: SessionZygote ->
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
                    state,
                    logger
                )
            }
        )
    }

    override fun handleCrash(crashId: String) {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.CRASH,
            timestamp = timestamp,
            oldSessionAction = { initial: SessionZygote ->
                payloadFactory.endPayloadWithCrash(state, timestamp, initial, crashId)
            },
            crashId = crashId
        )
    }

    override fun reportBackgroundActivityStateChange() {
        payloadCachingService?.reportBackgroundActivityStateChange()
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
        oldSessionAction: ((initial: SessionZygote) -> Envelope<SessionPayload>?)? = null,
        newSessionAction: (Provider<SessionZygote?>)? = null,
        earlyTerminationCondition: () -> Boolean = { false },
        clearUserInfo: Boolean = false,
        crashId: String? = null
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

            Systrace.startSynchronous("transition-state-start")

            // first, disable any previous periodic caching so the job doesn't overwrite the to-be saved session
            payloadCachingService?.stopCaching()

            // second, end the current session or background activity, if either exist.
            Systrace.startSynchronous("end-current-session")
            val initial = activeSession
            if (initial != null) {
                sessionSpanAttrPopulator.populateSessionSpanEndAttrs(transitionType.lifeEventType(state), crashId, initial.isColdStart)
                val endMessage = oldSessionAction?.invoke(initial)
                processEndMessage(endMessage, transitionType)
            }
            Systrace.endSynchronous()

            // the previous session has fully ended at this point
            // now, we can clear the SDK state and prepare for the next session
            Systrace.startSynchronous("prepare-new-session")
            boundaryDelegate.prepareForNewSession(clearUserInfo)
            Systrace.endSynchronous()

            // calculate new session state
            val endProcessState = transitionType.endState(state)
            val inForeground = endProcessState == ProcessState.FOREGROUND

            // create the next session span if we should, and update the SDK state to reflect the transition
            Systrace.startSynchronous("create-new-session")
            val newState = newSessionAction?.invoke()
            activeSession = newState
            val sessionId = newState?.sessionId
            sessionIdTracker.setActiveSession(sessionId, inForeground)
            newState?.let(sessionSpanAttrPopulator::populateSessionSpanStartAttrs)
            Systrace.endSynchronous()

            // initiate periodic caching of the payload if a new session has started
            Systrace.startSynchronous("initiate-periodic-caching")
            if (transitionType != TransitionType.CRASH && newState != null) {
                updatePeriodicCacheAttrs()
                payloadCachingService?.startCaching(newState, endProcessState) { state, timestamp, zygote ->
                    synchronized(lock) {
                        updatePeriodicCacheAttrs()
                        payloadFactory.snapshotPayload(state, timestamp, zygote)
                    }
                }
            }
            Systrace.endSynchronous()

            Systrace.startSynchronous("alter-session-state")
            // update the current state of the SDK. this should match the value in sessionIdTracker
            state = endProcessState

            // update data capture orchestrator
            val sessionType = when (endProcessState) {
                ProcessState.FOREGROUND -> SessionType.FOREGROUND
                ProcessState.BACKGROUND -> SessionType.BACKGROUND
            }
            dataCaptureOrchestrator.currentSessionType = sessionType
            Systrace.endSynchronous()

            // log the state change
            Systrace.startSynchronous("log-session-state")
            logSessionStateChange(
                sessionId,
                timestamp,
                !inForeground,
                transitionType.name,
                logger
            )
            Systrace.endSynchronous()

            // et voila! a new session is born
            Systrace.endSynchronous()
        }
    }

    private fun processEndMessage(envelope: Envelope<SessionPayload>?, transitionType: TransitionType) {
        envelope?.let {
            payloadStore?.storeSessionPayload(envelope, transitionType)
        }
    }

    private fun updatePeriodicCacheAttrs() {
        val now = clock.now().millisToNanos()
        val attr = SpanAttributeData(embHeartbeatTimeUnixNano.name, now.toString())
        sessionSpanWriter.addSystemAttribute(attr)
        sessionSpanWriter.addSystemAttribute(SpanAttributeData(embTerminated.name, true.toString()))
    }

    private fun logSessionStateChange(
        sessionId: String?,
        timestamp: Long,
        inBackground: Boolean,
        stateChange: String,
        logger: EmbLogger
    ) {
        val type = when {
            inBackground -> "background"
            else -> "session"
        }
        logger.logDebug("New session created: ID=$sessionId, timestamp=$timestamp, type=$type, state_change=$stateChange")
    }
}
