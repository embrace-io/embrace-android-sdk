package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.attrs.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.internal.arch.attrs.embTerminated
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider

internal class SessionOrchestratorImpl(
    appStateTracker: AppStateTracker,
    private val payloadFactory: PayloadFactory,
    private val clock: Clock,
    private val configService: ConfigService,
    private val sessionTracker: SessionTracker,
    private val boundaryDelegate: OrchestratorBoundaryDelegate,
    private val payloadStore: PayloadStore?,
    private val payloadCachingService: PayloadCachingService?,
    private val instrumentationRegistry: InstrumentationRegistry,
    private val destination: TelemetryDestination,
    private val sessionSpanAttrPopulator: SessionSpanAttrPopulator,
) : SessionOrchestrator {

    /**
     * Tracks whether the foreground phase comes from a cold start or not.
     */
    @Volatile
    private var coldStart = true

    private val lock = Any()

    private var state = appStateTracker.getAppState()

    init {
        appStateTracker.addListener(this)
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

    override fun onForeground() {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.ON_FOREGROUND,
            oldSessionAction = { initial: SessionZygote ->
                payloadFactory.endPayloadWithState(AppState.BACKGROUND, timestamp, initial)
            },
            newSessionAction = {
                payloadFactory.startPayloadWithState(AppState.FOREGROUND, timestamp, coldStart)
            },
            earlyTerminationCondition = {
                return@transitionState shouldRunOnForeground(state)
            }
        )
        coldStart = false
    }

    override fun onBackground() {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.ON_BACKGROUND,
            oldSessionAction = { initial: SessionZygote ->
                payloadFactory.endPayloadWithState(AppState.FOREGROUND, timestamp, initial)
            },
            newSessionAction = {
                payloadFactory.startPayloadWithState(AppState.BACKGROUND, timestamp, false)
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
                    sessionTracker.getActiveSession()?.startTime,
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

    override fun onSessionDataUpdate() {
        if (state == AppState.BACKGROUND) {
            payloadCachingService?.reportBackgroundActivityStateChange()
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

            val endingSession = sessionTracker.getActiveSession()
            if (endingSession != null) {
                sessionSpanAttrPopulator.populateSessionSpanEndAttrs(
                    endType = transitionType.lifeEventType(state),
                    crashId = crashId,
                    coldStart = endingSession.isColdStart
                )
            }

            // calculate new session state
            val endAppState = transitionType.endState(state)
            val newSession = sessionTracker.newActiveSession(
                endSessionCallback = {
                    // End the current session or background activity, if either exist.
                    EmbTrace.trace("end-current-session") {
                        processEndMessage(oldSessionAction?.invoke(this), transitionType)
                    }
                },
                startSessionCallback = {
                    // the previous session has fully ended at this point
                    // now, we can clear the SDK state and prepare for the next session
                    EmbTrace.trace("prepare-new-session") {
                        boundaryDelegate.cleanupAfterSessionEnd(clearUserInfo)
                    }

                    // create the next session span if we should, and update the SDK state to reflect the transition
                    EmbTrace.trace("create-new-session") {
                        newSessionAction?.invoke()
                    }
                },
                postTransitionAppState = endAppState
            )

            // update the current state of the SDK
            state = endAppState

            // update newly created session
            if (newSession != null) {
                boundaryDelegate.prepareForNewSession()
                sessionSpanAttrPopulator.populateSessionSpanStartAttrs(newSession)
                instrumentationRegistry.onNewSession()
                if (transitionType != TransitionType.CRASH) {
                    // initiate periodic caching of the payload if a new session has started
                    EmbTrace.start("initiate-periodic-caching")
                    updatePeriodicCacheAttrs()
                    payloadCachingService?.startCaching(newSession, endAppState) { state, timestamp, zygote ->
                        synchronized(lock) {
                            updatePeriodicCacheAttrs()
                            payloadFactory.snapshotPayload(state, timestamp, zygote)
                        }
                    }
                    EmbTrace.end()
                }
            } else if (transitionType == TransitionType.ON_BACKGROUND) {
                // if a new session hasn't been created when we background, cache an empty envelope to be used
                // in case a native crash needs to be sent in the future after the current process dies
                payloadStore?.cacheEmptyCrashEnvelope(payloadFactory.createEmptyLogEnvelope())
            }

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
