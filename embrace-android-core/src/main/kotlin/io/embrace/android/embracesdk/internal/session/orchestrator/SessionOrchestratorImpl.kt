@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.SessionPartPayload
import io.embrace.android.embracesdk.internal.session.SessionPartToken
import io.embrace.android.embracesdk.internal.session.UserSessionListener
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
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.math.max

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
    private val logger: InternalLogger,
    private val backgroundWorker: BackgroundWorker,
) : SessionOrchestrator {

    /**
     * Tracks whether the foreground phase comes from a cold start or not.
     */
    @Volatile
    private var coldStart = true

    private val lock = Any()
    private val userSessionListeners = CopyOnWriteArrayList<UserSessionListener>()

    private var state = appStateTracker.getAppState()

    @Volatile
    private var userSessionState: UserSessionState = UserSessionState.Initializing

    @Volatile
    private var inactivityTimerState: SessionTimerState? = null

    @Volatile
    private var maxDurationTimerState: SessionTimerState? = null

    init {
        loadPersistedUserSession()
        appStateTracker.addListener(this)
        sessionTracker.addSessionPartEndListener(instrumentationRegistry)
        sessionTracker.addSessionPartChangeListener(instrumentationRegistry)
        EmbTrace.trace("start-first-session") { createInitialSessionPart() }
    }

    private fun loadPersistedUserSession() {
        synchronized(lock) {
            try {
                val stored = metadataStore.load()
                userSessionState = when {
                    stored != null && clock.now() < stored.startTimeMs -> {
                        logger.trackInternalError(
                            InternalErrorType.CLOCK_BACKWARDS_SHIFT,
                            IllegalStateException(
                                "Clock shifted backwards from previous user session."
                            )
                        )
                        metadataStore.clear()
                        UserSessionState.NoActiveSession
                    }

                    stored != null && !isUserSessionOverMaxDuration(stored) -> {
                        scheduleMaxDurationTimeout(stored)
                        UserSessionState.Active(stored)
                    }

                    else -> UserSessionState.NoActiveSession
                }
            } catch (e: Exception) {
                userSessionState = UserSessionState.NoActiveSession
            }
        }
    }

    private fun createInitialSessionPart() {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.INITIAL,
            timestamp = timestamp,
            newSessionAction = {
                payloadFactory.startPayloadWithState(state, timestamp, true)
            }
        )
    }

    override fun onForeground() {
        val exceeded = synchronized(lock) {
            inactivityTimerState?.cancel()
            val exceeded = inactivityTimerState?.exceeded ?: false
            inactivityTimerState = null
            exceeded
        }

        val timestamp = clock.now()

        transitionState(
            transitionType = when {
                exceeded -> TransitionType.INACTIVITY_FOREGROUND
                else -> TransitionType.ON_FOREGROUND
            },
            timestamp = timestamp,
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
            timestamp = timestamp,
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
        scheduleInactivityTimeout()
    }

    override fun endSessionWithManual(clearUserInfo: Boolean) {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.END_MANUAL,
            timestamp = timestamp,
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
            timestamp = timestamp,
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

    override fun addUserSessionListener(listener: UserSessionListener) {
        userSessionListeners.add(listener)
        val state = userSessionState
        if (state is UserSessionState.Active) {
            try {
                listener.onSessionStateEvent(SessionStateEvent.UserSessionActive(state.metadata.userSessionId))
            } catch (e: Exception) {
                logger.trackInternalError(InternalErrorType.USER_SESSION_CALLBACK_FAIL, e)
            }
        }
    }

    private fun notifyListeners(event: SessionStateEvent) {
        userSessionListeners.forEach { listener ->
            try {
                listener.onSessionStateEvent(event)
            } catch (e: Exception) {
                logger.trackInternalError(InternalErrorType.USER_SESSION_CALLBACK_FAIL, e)
            }
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
        timestamp: Long,
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

                    // transition the user session before creating the new session part so that
                    // the user session is always ready
                    if (transitionType != TransitionType.CRASH) {
                        if (transitionType == TransitionType.END_MANUAL ||
                            transitionType == TransitionType.INACTIVITY_TIMEOUT ||
                            transitionType == TransitionType.INACTIVITY_FOREGROUND ||
                            transitionType == TransitionType.MAX_DURATION
                        ) {
                            handleUserSessionEnd(timestamp)
                        } else {
                            handleNewSessionPart(timestamp)
                        }
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

    private fun scheduleInactivityTimeout() {
        val metadata = (userSessionState as? UserSessionState.Active)?.metadata ?: return
        inactivityTimerState = SessionTimerState(
            backgroundWorker.schedule<Unit>(
                ::onInactivityTimeout,
                metadata.inactivityTimeoutSecs,
                TimeUnit.SECONDS,
            )
        )
    }

    private fun scheduleMaxDurationTimeout(metadata: UserSessionMetadata) {
        val remainingSecs = metadata.maxDurationSecs - ((clock.now() - metadata.startTimeMs) / 1_000L)
        val delay = max(remainingSecs, 0)
        maxDurationTimerState = SessionTimerState(
            backgroundWorker.schedule<Unit>(
                ::onMaxDurationTimeout,
                delay,
                TimeUnit.SECONDS,
            )
        )
    }

    private fun onMaxDurationTimeout() {
        synchronized(lock) {
            if (userSessionState !is UserSessionState.Active) {
                return
            }
            val currentAppState = state
            val timestamp = clock.now()
            transitionState(
                transitionType = TransitionType.MAX_DURATION,
                timestamp = timestamp,
                oldSessionAction = { initial: SessionPartToken ->
                    payloadFactory.endPayloadWithState(currentAppState, timestamp, initial)
                },
                newSessionAction = {
                    payloadFactory.startPayloadWithState(currentAppState, timestamp, false)
                },
            )
        }
    }

    private fun onInactivityTimeout() {
        synchronized(lock) {
            if (state != AppState.BACKGROUND) {
                return
            }
            inactivityTimerState?.exceeded = true

            if (configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()) {
                val timestamp = clock.now()
                transitionState(
                    transitionType = TransitionType.INACTIVITY_TIMEOUT,
                    timestamp = timestamp,
                    oldSessionAction = { initial: SessionPartToken ->
                        payloadFactory.endPayloadWithState(AppState.BACKGROUND, timestamp, initial)
                    },
                    newSessionAction = {
                        payloadFactory.startPayloadWithState(AppState.BACKGROUND, timestamp, false)
                    },
                    earlyTerminationCondition = { state != AppState.BACKGROUND },
                )
            }
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
    private fun handleNewSessionPart(timestamp: Long) {
        val current = userSessionState
        if (current is UserSessionState.Active) {
            if (isUserSessionOverMaxDuration(current.metadata)) {
                terminateUserSession(current)
                startNewUserSession(timestamp)
            } else {
                val updatedMetadata = current.metadata.copy(
                    partNumber = current.metadata.partNumber + 1,
                )
                metadataStore.save(updatedMetadata)
                userSessionState = UserSessionState.Active(updatedMetadata)
            }
        } else {
            startNewUserSession(timestamp)
        }
    }

    /**
     * Called when the developer manually ends the session. Terminates any active user session,
     * then always starts a new one.
     */
    private fun handleUserSessionEnd(timestamp: Long) {
        val state = userSessionState
        if (state is UserSessionState.Active) {
            terminateUserSession(state)
        }
        startNewUserSession(timestamp)
    }

    private fun isUserSessionOverMaxDuration(metadata: UserSessionMetadata): Boolean =
        clock.now() - metadata.startTimeMs >= metadata.maxDurationSecs * 1_000L

    private fun startNewUserSession(startTimeMs: Long) {
        val maxDurationMs = configService.sessionBehavior.getMaxSessionDurationMs()
        val inactivityTimeoutMs = configService.sessionBehavior.getSessionInactivityTimeoutMs()
        val newMetadata = UserSessionMetadata(
            startTimeMs = startTimeMs,
            userSessionId = Uuid.getEmbUuid(),
            userSessionNumber = ordinalStore.incrementAndGet(Ordinal.USER_SESSION).toLong(),
            maxDurationSecs = maxDurationMs / 1_000L,
            inactivityTimeoutSecs = inactivityTimeoutMs / 1_000L,
            partNumber = 1,
        )
        metadataStore.save(newMetadata)
        userSessionState = UserSessionState.Active(newMetadata)
        notifyListeners(SessionStateEvent.UserSessionActive(newMetadata.userSessionId))

        if (state == AppState.FOREGROUND) {
            scheduleMaxDurationTimeout(newMetadata)
        }
    }

    private fun terminateUserSession(state: UserSessionState.Active) {
        maxDurationTimerState?.cancel()
        maxDurationTimerState = null
        metadataStore.clear()
        userSessionState = UserSessionState.Terminated
        notifyListeners(SessionStateEvent.UserSessionEnded(state.metadata.userSessionId))
    }
}
