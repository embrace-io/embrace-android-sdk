package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.SessionType
import io.embrace.android.embracesdk.internal.arch.attrs.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.arch.attrs.embTerminated
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.id.SessionIdTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.utils.EmbTrace
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
    private val instrumentationRegistry: InstrumentationRegistry,
    private val destination: TelemetryDestination,
    private val sessionSpanAttrPopulator: SessionSpanAttrPopulator,
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
        EmbTrace.trace("start-first-session") { createInitialSession() }
    }

    private fun createInitialSession() {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.INITIAL,
            newSessionAction = {
                payloadFactory.startPayloadWithState(state, timestamp, true)
            }
        )
    }

    override fun onForeground(coldStart: Boolean, timestamp: Long) {
        transitionState(
            transitionType = TransitionType.ON_FOREGROUND,
            oldSessionAction = { initial: SessionZygote ->
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
            oldSessionAction = { initial: SessionZygote ->
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
                    state
                )
            }
        )
    }

    override fun handleCrash(crashId: String) {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.CRASH,
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
     * @param oldSessionAction  The action that ends the old session or background activity (if any).
     * The initial session object (if any) is passed as a parameter to allow building a full payload.
     * @param newSessionAction  The action that starts the new session or background activity (if any).
     * If a new session is created this must return a session object containing the initial state.
     * @param clearUserInfo     Whether to clear user info when ending the session. Defaults to false
     */
    private fun transitionState(
        transitionType: TransitionType,
        oldSessionAction: ((initial: SessionZygote) -> Envelope<SessionPayload>?)? = null,
        newSessionAction: (Provider<SessionZygote?>)? = null,
        earlyTerminationCondition: () -> Boolean = { false },
        clearUserInfo: Boolean = false,
        crashId: String? = null,
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

            EmbTrace.start("transition-state-start")

            // first, disable any previous periodic caching so the job doesn't overwrite the to-be saved session
            payloadCachingService?.stopCaching()

            // second, end the current session or background activity, if either exist.
            EmbTrace.start("end-current-session")
            val initial = activeSession
            if (initial != null) {
                sessionSpanAttrPopulator.populateSessionSpanEndAttrs(
                    transitionType.lifeEventType(state),
                    crashId,
                    initial.isColdStart
                )
                val endMessage = oldSessionAction?.invoke(initial)
                processEndMessage(endMessage, transitionType)
            }
            EmbTrace.end()

            // the previous session has fully ended at this point
            // now, we can clear the SDK state and prepare for the next session
            EmbTrace.start("prepare-new-session")
            boundaryDelegate.cleanupAfterSessionEnd(clearUserInfo)
            EmbTrace.end()

            // calculate new session state
            val endProcessState = transitionType.endState(state)
            val inForeground = endProcessState == ProcessState.FOREGROUND

            // create the next session span if we should, and update the SDK state to reflect the transition
            EmbTrace.start("create-new-session")
            val newState = newSessionAction?.invoke()
            activeSession = newState
            val sessionId = newState?.sessionId
            sessionIdTracker.setActiveSession(sessionId, inForeground)

            if (newState != null) {
                boundaryDelegate.prepareForNewSession()
                sessionSpanAttrPopulator.populateSessionSpanStartAttrs(newState)
                // initiate periodic caching of the payload if a new session has started
                EmbTrace.start("initiate-periodic-caching")
                if (transitionType != TransitionType.CRASH) {
                    updatePeriodicCacheAttrs()
                    payloadCachingService?.startCaching(newState, endProcessState) { state, timestamp, zygote ->
                        synchronized(lock) {
                            updatePeriodicCacheAttrs()
                            payloadFactory.snapshotPayload(state, timestamp, zygote)
                        }
                    }
                }
                EmbTrace.end()
            }

            if (activeSession == null && transitionType == TransitionType.ON_BACKGROUND) {
                // if a new session hasn't been created when we background, cache an empty envelope to be used
                // in case a native crash needs to be sent in the future after the current process dies
                payloadStore?.cacheEmptyCrashEnvelope(payloadFactory.createEmptyLogEnvelope())
            }
            EmbTrace.end()

            EmbTrace.start("alter-session-state")
            // update the current state of the SDK. this should match the value in sessionIdTracker
            state = endProcessState

            // update data capture orchestrator
            val sessionType = when (endProcessState) {
                ProcessState.FOREGROUND -> SessionType.FOREGROUND
                ProcessState.BACKGROUND -> SessionType.BACKGROUND
            }
            instrumentationRegistry.currentSessionType = sessionType
            EmbTrace.end()

            // et voila! a new session is born
            EmbTrace.end()
        }
    }

    private fun processEndMessage(envelope: Envelope<SessionPayload>?, transitionType: TransitionType) {
        envelope?.let {
            payloadStore?.storeSessionPayload(envelope, transitionType)
        }
    }

    private fun updatePeriodicCacheAttrs() {
        val now = clock.now().millisToNanos()
        destination.addSessionAttribute(embHeartbeatTimeUnixNano.name, now.toString())
        destination.addSessionAttribute(embTerminated.name, true.toString())
    }
}
