@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionMetadata
import io.embrace.android.embracesdk.internal.session.UserSessionMetadataStore
import io.embrace.android.embracesdk.internal.session.UserSessionState
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv

internal class SessionOrchestratorImpl(
    appStateTracker: AppStateTracker,
    private val payloadFactory: PayloadFactory,
    private val clock: Clock,
    private val configService: ConfigService,
    private val sessionTracker: SessionPartTracker,
    private val boundaryDelegate: OrchestratorBoundaryDelegate,
    private val payloadStore: PayloadStore?,
    private val payloadCachingService: PayloadCachingService?,
    instrumentationRegistry: InstrumentationRegistry,
    private val destination: TelemetryDestination,
    private val sessionSpanAttrPopulator: SessionPartSpanAttrPopulator,
    private val ordinalStore: OrdinalStore,
    private val metadataStore: UserSessionMetadataStore,
) : SessionOrchestrator {

    /**
     * Tracks whether the foreground phase comes from a cold start or not.
     */
    @Volatile
    private var coldStart = true

    private val lock = Any()

    private var state = appStateTracker.getAppState()
    private val maxDurationMs: Long = configService.sessionBehavior.getMaxSessionDurationMs()
    private val inactivityTimeoutMs: Long = configService.sessionBehavior.getSessionInactivityTimeoutMs()

    @Volatile
    private var userSessionState: UserSessionState = UserSessionState.Initializing

    init {
        loadPersistedUserSession()
        appStateTracker.addListener(this)
        sessionTracker.addSessionPartEndListener(instrumentationRegistry)
        sessionTracker.addSessionPartChangeListener(instrumentationRegistry)
        EmbTrace.trace("start-first-session") { createInitialSession() }
    }

    private fun loadPersistedUserSession() {
        synchronized(lock) {
            try {
                val stored = metadataStore.load()
                userSessionState = when {
                    stored != null && !isUserSessionOverMaxDuration(stored) -> UserSessionState.Active(stored)
                    else -> UserSessionState.NoActiveSession
                }
            } catch (e: Exception) {
                userSessionState = UserSessionState.NoActiveSession
            }
        }
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
            oldSessionAction = { initial: SessionPartToken ->
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
            oldSessionAction = { initial: SessionPartToken ->
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
            oldSessionAction = { initial: SessionPartToken ->
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
            oldSessionAction = { initial: SessionPartToken ->
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

    override fun currentUserSession(): UserSessionMetadata? =
        (userSessionState as? UserSessionState.Active)?.metadata

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
        oldSessionAction: ((initial: SessionPartToken) -> Envelope<SessionPartPayload>?)? = null,
        newSessionAction: (Provider<SessionPartToken?>)? = null,
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
                if (transitionType != TransitionType.CRASH) {
                    // handle user session transition if needed
                    if (transitionType == TransitionType.END_MANUAL) {
                        handleUserSessionManualEnd()
                    } else {
                        handleNewSessionPart()
                    }

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

    private fun processEndMessage(envelope: Envelope<SessionPartPayload>?, transitionType: TransitionType) {
        envelope?.let {
            payloadStore?.storeSessionPartPayload(envelope, transitionType)
        }
    }

    private fun updatePeriodicCacheAttrs() {
        val now = clock.now().millisToNanos()
        destination.addSessionPartAttribute(EmbSessionAttributes.EMB_HEARTBEAT_TIME_UNIX_NANO, now.toString())
        destination.addSessionPartAttribute(EmbSessionAttributes.EMB_TERMINATED, true.toString())
    }

    /**
     * Called whenever a new session part is successfully created for a non-manual, non-crash
     * transition. If the active user session has exceeded max duration, terminates it and starts
     * a new one. If no user session is active, starts a new one. Otherwise keeps the current one.
     */
    private fun handleNewSessionPart() {
        val current = userSessionState
        if (current is UserSessionState.Active) {
            if (isUserSessionOverMaxDuration(current.metadata)) {
                terminateUserSession()
                startNewUserSession()
            }
        } else {
            startNewUserSession()
        }
    }

    /**
     * Called when the developer manually ends the session. Terminates any active user session,
     * then always starts a new one.
     */
    private fun handleUserSessionManualEnd() {
        if (userSessionState is UserSessionState.Active) {
            terminateUserSession()
        }
        startNewUserSession()
    }

    private fun isUserSessionOverMaxDuration(metadata: UserSessionMetadata): Boolean =
        clock.now() - metadata.startTimeMs >= metadata.maxDurationSecs * 1_000L

    private fun startNewUserSession() {
        val maxDurationMs = configService.sessionBehavior.getMaxSessionDurationMs()
        val inactivityTimeoutMs = configService.sessionBehavior.getSessionInactivityTimeoutMs()
        val newMetadata = UserSessionMetadata(
            startTimeMs = clock.now(),
            userSessionId = Uuid.getEmbUuid(),
            userSessionNumber = ordinalStore.incrementAndGet(Ordinal.USER_SESSION).toLong(),
            maxDurationSecs = maxDurationMs / 1_000L,
            inactivityTimeoutSecs = inactivityTimeoutMs / 1_000L,
        )
        metadataStore.save(newMetadata)
        userSessionState = UserSessionState.Active(newMetadata)
    }

    private fun terminateUserSession() {
        metadataStore.clear()
        userSessionState = UserSessionState.Terminated
    }
}
