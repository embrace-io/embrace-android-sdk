@file:OptIn(ExperimentalSemconv::class)

package io.embrace.android.embracesdk.internal.session.orchestrator

import io.embrace.android.embracesdk.SessionStateEvent
import io.embrace.android.embracesdk.SessionStateEvent.UserSessionActive
import io.embrace.android.embracesdk.SessionStateEvent.UserSessionEnded
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.arch.startup.StartupType
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
import io.embrace.android.embracesdk.internal.session.UserSessionRestoreDecision
import io.embrace.android.embracesdk.internal.session.UserSessionState
import io.embrace.android.embracesdk.internal.session.UserSessionState.Active
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.message.PayloadFactory
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes.EmbUserSessionTerminationReasonValues
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
    private val sessionPartSpanAttrPopulator: SessionPartSpanAttrPopulator,
    private val ordinalStore: OrdinalStore,
    private val metadataStore: UserSessionMetadataStore,
    private val logger: InternalLogger,
    private val backgroundWorker: BackgroundWorker,
    private val uuidSource: UuidSource,
    private val startupClassifier: StartupClassifier,
) : SessionOrchestrator {

    /**
     * Tracks whether the foreground phase comes from a cold start or not.
     */
    private var coldStart = true
    private val lock = Any()
    private val userSessionListeners = CopyOnWriteArrayList<UserSessionListener>()

    @Volatile
    private var state = appStateTracker.getAppState()

    @Volatile
    private var userSessionState: UserSessionState = UserSessionState.Initializing

    @Volatile
    private var lastManualEndMs: Long? = null

    private var inactivityTimerState: SessionTimerState? = null
    private var maxDurationTimerState: SessionTimerState? = null
    private var backgroundStartupWindowTimerState: SessionTimerState? = null

    @Volatile
    override var userSessionRestoreDecision: UserSessionRestoreDecision? = null
        private set

    init {
        loadPersistedUserSession()
        appStateTracker.addListener(this)
        sessionTracker.addSessionPartEndListener(instrumentationRegistry)
        sessionTracker.addSessionPartChangeListener(instrumentationRegistry)
    }

    override fun start() {
        EmbTrace.trace("start-first-session") { createInitialSessionPart() }
    }

    private fun loadPersistedUserSession() {
        synchronized(lock) {
            try {
                val stored = metadataStore.load()
                val overMaxDuration = stored?.isOverMaxDuration(clock) ?: false
                val inactive = stored?.isInactive(clock) ?: false
                userSessionState = when {
                    stored == null -> UserSessionState.NoActiveSession

                    // Current timestamp not congruent with stored user session's timestamp, so clear the cache
                    clock.now() < stored.startTimeMs -> {
                        logger.trackInternalError(
                            InternalErrorType.ClockBackwardsShift,
                            IllegalStateException(
                                "Clock shifted backwards from previous user session."
                            )
                        )
                        metadataStore.clear()
                        userSessionRestoreDecision = stored.toTerminatedDecision(EmbUserSessionTerminationReasonValues.CLOCK_MISMATCH)
                        UserSessionState.NoActiveSession
                    }

                    // Continue a background-only user session if it won't extend it beyond its max duration
                    // Don't set any expiry timers those aren't applicable when the user isn't considered active
                    stored.isBackgroundOnly && !overMaxDuration -> {
                        userSessionRestoreDecision = stored.toRestoredDecision()
                        Active(stored)
                    }

                    // Continue a user session if it won't extend it beyond its max duration and is within its inactivity grace period
                    !stored.isBackgroundOnly && !overMaxDuration && !inactive -> {
                        userSessionRestoreDecision = stored.toRestoredDecision()
                        scheduleMaxDurationTimeout(stored)
                        Active(stored)
                    }

                    else -> {
                        // A stored session that isn't being continued ended in the dead process - record the matching reason so
                        // resurrection can stamp its final part as terminated.
                        userSessionRestoreDecision = stored.toTerminatedDecision(
                            if (overMaxDuration) {
                                EmbUserSessionTerminationReasonValues.MAX_DURATION_REACHED
                            } else {
                                EmbUserSessionTerminationReasonValues.INACTIVITY
                            }
                        )
                        UserSessionState.NoActiveSession
                    }
                }
            } catch (e: Exception) {
                userSessionState = UserSessionState.NoActiveSession
            }
        }
    }

    private fun UserSessionMetadata.Classified.toRestoredDecision(): UserSessionRestoreDecision.Restored =
        UserSessionRestoreDecision.Restored(userSessionId = userSessionId, backgroundOnly = isBackgroundOnly)

    private fun UserSessionMetadata.Classified.toTerminatedDecision(reason: String): UserSessionRestoreDecision.Terminated =
        UserSessionRestoreDecision.Terminated(userSessionId = userSessionId, backgroundOnly = isBackgroundOnly, reason = reason)

    private fun createInitialSessionPart() {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.INITIAL,
            timestamp = timestamp,
            newSessionAction = {
                payloadFactory.startPayloadWithState(
                    state = state,
                    timestamp = timestamp,
                    coldStart = true,
                    userSessionPartIndex = ::incrementPartIndex,
                    sessionPartNumber = ::incrementSessionPartNumber,
                )
            }
        )
    }

    override fun onForeground() {
        // Hold the lock as we cancel the inactivity timer and determine the transition type. The lock would be acquired in
        // transitionState() anyway so we're just hanging on to it for the entire duration instead of dropping and reacquiring it again.
        synchronized(lock) {
            inactivityTimerState?.cancel()
            inactivityTimerState = null
            val metadata = currentUserSession()
            val transitionType = if (metadata?.isBackgroundOnly == true) {
                TransitionType.BACKGROUND_ONLY_SESSION_END
            } else if (metadata?.isInactive(clock) == true) {
                TransitionType.INACTIVITY_FOREGROUND
            } else if (metadata?.isOverMaxDuration(clock) == true) {
                TransitionType.MAX_DURATION
            } else {
                TransitionType.ON_FOREGROUND
            }

            val timestamp = clock.now()

            transitionState(
                transitionType = transitionType,
                timestamp = timestamp,
                oldSessionAction = { initial: SessionPartToken ->
                    payloadFactory.endPayloadWithState(AppState.BACKGROUND, timestamp, initial)
                },
                newSessionAction = {
                    payloadFactory.startPayloadWithState(
                        state = AppState.FOREGROUND,
                        timestamp = timestamp,
                        coldStart = coldStart,
                        userSessionPartIndex = ::incrementPartIndex,
                        sessionPartNumber = ::incrementSessionPartNumber,
                    )
                },
                earlyTerminationCondition = {
                    return@transitionState shouldRunOnForeground(state)
                }
            )
            coldStart = false
        }
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
                payloadFactory.startPayloadWithState(
                    state = AppState.BACKGROUND,
                    timestamp = timestamp,
                    coldStart = false,
                    userSessionPartIndex = ::incrementPartIndex,
                    sessionPartNumber = ::incrementSessionPartNumber,
                )
            },
            earlyTerminationCondition = {
                return@transitionState shouldRunOnBackground(state)
            }
        )
    }

    override fun endSessionWithManual() {
        val timestamp = clock.now()
        transitionState(
            transitionType = TransitionType.END_MANUAL,
            timestamp = timestamp,
            oldSessionAction = { initial: SessionPartToken ->
                payloadFactory.endSessionWithManual(timestamp, initial)
            },
            newSessionAction = {
                lastManualEndMs = timestamp
                payloadFactory.startSessionWithManual(
                    state = state,
                    timestamp = timestamp,
                    userSessionPartIndex = ::incrementPartIndex,
                    sessionPartNumber = ::incrementSessionPartNumber,
                )
            },
            earlyTerminationCondition = {
                return@transitionState shouldEndManualSession(
                    configService,
                    clock,
                    currentUserSession()?.startTimeMs,
                    lastManualEndMs,
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
        (userSessionState as? Active)?.metadata

    override fun addUserSessionListener(listener: UserSessionListener) {
        synchronized(lock) {
            userSessionListeners.add(listener)
            currentUserSession()?.let { metadata ->
                try {
                    listener.onSessionStateEvent(UserSessionActive(metadata.userSessionId))
                } catch (e: Exception) {
                    logger.trackInternalError(InternalErrorType.UserSessionCallbackFail, e)
                }
            }
        }
    }

    private fun notifyListeners(event: SessionStateEvent) {
        userSessionListeners.forEach { listener ->
            try {
                listener.onSessionStateEvent(event)
            } catch (e: Exception) {
                logger.trackInternalError(InternalErrorType.UserSessionCallbackFail, e)
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
     */
    private fun transitionState(
        transitionType: TransitionType,
        timestamp: Long,
        oldSessionAction: ((initial: SessionPartToken) -> Envelope<SessionPartPayload>?)? = null,
        newSessionAction: (Provider<SessionPartToken?>)? = null,
        earlyTerminationCondition: () -> Boolean = { false },
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

            val endingSession = sessionTracker.getActiveSessionPart()
            if (endingSession != null) {
                sessionPartSpanAttrPopulator.populateSessionPartSpanEndAttrs(
                    endType = transitionType.lifeEventType(state),
                    crashId = crashId,
                    coldStart = endingSession.isColdStart,
                    endAttributes = transitionType.endAttributes,
                )
            }

            // calculate new session state
            val endAppState = transitionType.postTransitionEndState(state)
            val newSessionPart = sessionTracker.newActiveSessionPart(
                endSessionPartCallback = {
                    // End the current session or background activity, if either exist.
                    EmbTrace.trace("end-current-session") {
                        processEndMessage(oldSessionAction?.invoke(this), transitionType)
                    }
                },
                startSessionPartCallback = {
                    // the previous session has fully ended at this point
                    // now, we can clear the SDK state and prepare for the next session
                    EmbTrace.trace("prepare-new-session") {
                        boundaryDelegate.cleanupAfterSessionEnd()
                    }

                    // transition the user session before creating the new session part so that
                    // the user session is always ready
                    transitionUserSession(transitionType, endAppState, timestamp)

                    // create the next session span if we should, and update the SDK state to reflect the transition
                    EmbTrace.trace("create-new-session") {
                        newSessionAction?.invoke()
                    }
                },
                postTransitionAppState = endAppState
            )

            // update the current state of the SDK
            state = endAppState

            // update newly created session part and user session, if applicable
            val userSession = currentUserSession()
            if (newSessionPart != null) {
                if (userSession != null) {
                    // persist partIndex to handle user session restoration in new process
                    setActiveUserSession(userSession.withPartIndex(newSessionPart.userSessionPartIndex))

                    if (endAppState == AppState.FOREGROUND) {
                        sessionTracker.setProcessStateSummary(newSessionPart.sessionPartId, userSession.userSessionId)
                    }
                }
                boundaryDelegate.prepareForNewSession()
                sessionPartSpanAttrPopulator.populateSessionPartSpanStartAttrs(newSessionPart, userSession)
                if (transitionType != TransitionType.CRASH) {
                    // initiate periodic caching of the payload if a new session has started
                    EmbTrace.start("initiate-periodic-caching")
                    updatePeriodicCacheAttrs()
                    payloadCachingService?.startCaching(newSessionPart, endAppState) { state, timestamp, zygote ->
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

    private fun scheduleInactivityTimeout(metadata: UserSessionMetadata.Classified?) {
        inactivityTimerState?.cancel()
        if (metadata != null && !metadata.isBackgroundOnly) {
            inactivityTimerState = SessionTimerState(
                backgroundWorker.schedule<Unit>(
                    ::onInactivityTimeout,
                    metadata.inactivityTimeoutSecs,
                    TimeUnit.SECONDS,
                )
            )
        }
    }

    private fun scheduleMaxDurationTimeout(metadata: UserSessionMetadata?) {
        if (metadata is UserSessionMetadata.Classified && !metadata.isBackgroundOnly) {
            maxDurationTimerState?.cancel()
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
    }

    /**
     * Cancels and clears the background-startup window timer once it's no longer needed
     */
    private fun clearBackgroundStartupWindowTimer() {
        backgroundStartupWindowTimerState?.cancel()
        backgroundStartupWindowTimerState = null
    }

    private fun onMaxDurationTimeout() {
        synchronized(lock) {
            val metadata = currentUserSessionIfClassified()
            if (metadata == null || metadata.isBackgroundOnly) {
                return
            }
            val currentAppState = state
            val timestamp = clock.now()
            val captureNextPart = currentAppState != AppState.BACKGROUND ||
                configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()
            transitionState(
                transitionType = TransitionType.MAX_DURATION,
                timestamp = timestamp,
                oldSessionAction = { initial: SessionPartToken ->
                    payloadFactory.endPayloadWithState(currentAppState, timestamp, initial)
                },
                newSessionAction = {
                    if (captureNextPart) {
                        payloadFactory.startPayloadWithState(
                            state = currentAppState,
                            timestamp = timestamp,
                            coldStart = false,
                            userSessionPartIndex = ::incrementPartIndex,
                            sessionPartNumber = ::incrementSessionPartNumber,
                        )
                    } else {
                        null
                    }
                },
            )
        }
    }

    private fun onInactivityTimeout() {
        synchronized(lock) {
            val metadata = currentUserSessionIfClassified()
            if (metadata == null || metadata.isBackgroundOnly) {
                return
            }
            val timestamp = clock.now()
            transitionState(
                transitionType = TransitionType.INACTIVITY_TIMEOUT,
                timestamp = timestamp,
                oldSessionAction = { initial: SessionPartToken ->
                    payloadFactory.endPayloadWithState(AppState.BACKGROUND, timestamp, initial)
                },
                newSessionAction = {
                    if (configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()) {
                        payloadFactory.startPayloadWithState(
                            state = AppState.BACKGROUND,
                            timestamp = timestamp,
                            coldStart = false,
                            userSessionPartIndex = ::incrementPartIndex,
                            sessionPartNumber = ::incrementSessionPartNumber,
                        )
                    } else {
                        null
                    }
                },
                earlyTerminationCondition = { state != AppState.BACKGROUND },
            )
        }
    }

    private fun incrementPartIndex(): Int = (currentUserSession()?.partIndex ?: 0) + 1

    private fun incrementSessionPartNumber(): Int {
        return ordinalStore.incrementAndGet(Ordinal.SESSION_PART) {
            currentUserSession()?.userSessionNumber?.toInt() ?: 1
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
     * Decides what to do with the user session at the start of a session-part transition.
     */
    private fun transitionUserSession(transitionType: TransitionType, endAppState: AppState, timestamp: Long) {
        when {
            // User session does not need to be modified when the part transition is happening due to a crash
            transitionType == TransitionType.CRASH -> {}

            // User session needs to be established when the SDK starts up.
            // Create an unclassified user session and schedule a task to classify as background-only if it's not classified when it runs.
            transitionType == TransitionType.INITIAL && state == AppState.BACKGROUND -> {
                if (userSessionState !is Active) {
                    val newMetadata = createUnclassifiedUserSession(timestamp)
                    setActiveUserSession(newMetadata)
                    notifyListeners(UserSessionActive(newMetadata.userSessionId))
                    backgroundStartupWindowTimerState = SessionTimerState(
                        backgroundWorker.schedule<Unit>(
                            ::makeCurrentUserSessionBackgroundOnly,
                            BACKGROUND_STARTUP_WINDOW_MS,
                            TimeUnit.MILLISECONDS,
                        )
                    )
                }
            }

            // User session being ended explicitly - end current one and start a new one based on the expected endAppState.
            transitionType.endsUserSession -> {
                clearBackgroundStartupWindowTimer()
                if (endAppState == AppState.BACKGROUND && !configService.backgroundActivityBehavior.isBackgroundActivityCaptureEnabled()) {
                    terminateActiveUserSession()
                } else {
                    transitionToNewUserSession(timestamp, endAppState)
                }
                scheduleSessionEndTimers(endAppState)
            }

            // Transitions where the current user session may continue
            else -> {
                clearBackgroundStartupWindowTimer()

                // Update the current user session either by modifying the existing one or replacing it with a new one
                when (val current = currentUserSession()) {
                    is UserSessionMetadata.Unclassified -> setActiveUserSession(
                        current.classify(
                            isBackgroundOnly = endAppState == AppState.BACKGROUND,
                            updatedLastActivityMs = timestamp
                        )
                    )

                    is UserSessionMetadata.Classified ->
                        if (current.continueUserSession(timestamp, endAppState)) {
                            setActiveUserSession(current.withNewActivity(timestamp))
                        } else {
                            transitionToNewUserSession(timestamp, endAppState)
                        }

                    null -> transitionToNewUserSession(timestamp, endAppState)
                }

                scheduleSessionEndTimers(endAppState)
            }
        }
    }

    private fun createUnclassifiedUserSession(startTimeMs: Long): UserSessionMetadata.Unclassified =
        UserSessionMetadata.Unclassified(
            startTimeMs = startTimeMs,
            userSessionId = uuidSource.createUuid(),
            userSessionNumber = ordinalStore.incrementAndGet(Ordinal.USER_SESSION).toLong(),
            maxDurationSecs = configService.sessionBehavior.getMaxSessionDurationMs() / 1_000L,
            inactivityTimeoutSecs = configService.sessionBehavior.getSessionInactivityTimeoutMs() / 1_000L,
            partIndex = 0,
            lastActivityMs = startTimeMs,
        )

    /**
     * The background-startup window elapsed without the app entering the foreground and classifying the user session as not
     * background-only, so we assume the process creation was not triggered by an app launch and classify it as background-only.
     */
    private fun makeCurrentUserSessionBackgroundOnly() {
        synchronized(lock) {
            val userSession = currentUserSession()
            if (userSession is UserSessionMetadata.Unclassified) {
                startupClassifier.assumeBackgroundStartup()
                if (startupClassifier.startupType() == StartupType.BACKGROUND) {
                    setActiveUserSession(userSession.classify(isBackgroundOnly = true))
                    destination.addSessionPartAttribute(
                        EmbSessionAttributes.EMB_IS_BACKGROUND_ONLY_PART,
                        UserSessionMetadata.BACKGROUND_ONLY_MARKER,
                    )
                }
            }
        }
    }

    /**
     * Schedule the appropriate user session ending timers given the app state
     */
    private fun scheduleSessionEndTimers(endAppState: AppState) {
        val userSession = currentUserSessionIfClassified()
        if (endAppState == AppState.FOREGROUND && maxDurationTimerState == null) {
            scheduleMaxDurationTimeout(userSession)
        } else if (endAppState == AppState.BACKGROUND) {
            scheduleInactivityTimeout(userSession)
        }
    }

    /**
     * Called when the active user session ends for any reason other than a crash. Terminates it, then start a new one based on
     * the post transition [AppState]
     */
    private fun transitionToNewUserSession(timestamp: Long, endAppState: AppState) {
        // End current user session if it exists
        terminateActiveUserSession()

        // Start new user session, make it active, and broadcast new user session being active to listeners. The caller is
        // responsible for scheduling the new session's end timers (see scheduleSessionEndTimers).
        val newMetadata = createUnclassifiedUserSession(timestamp).classify(isBackgroundOnly = endAppState == AppState.BACKGROUND)
        setActiveUserSession(newMetadata)
        notifyListeners(UserSessionActive(newMetadata.userSessionId))
    }

    /**
     * Ends the active user session  and leaves the SDK with no active user session ([UserSessionState.Terminated]).
     */
    private fun terminateActiveUserSession() {
        currentUserSession()?.let {
            maxDurationTimerState?.cancel()
            maxDurationTimerState = null
            inactivityTimerState?.cancel()
            inactivityTimerState = null
            metadataStore.clear()
            userSessionState = UserSessionState.Terminated
            notifyListeners(UserSessionEnded(it.userSessionId))
        }
    }

    /**
     * Persists [metadata] and adopts it as the active user session. An unclassified user session is persisted as background-only
     * so that a process death inside the background-startup window reads as a background start, while remaining unclassified in memory
     * until the foreground transition or the window resolves it.
     */
    private fun setActiveUserSession(metadata: UserSessionMetadata) {
        val persisted = when (metadata) {
            is UserSessionMetadata.Classified -> metadata
            is UserSessionMetadata.Unclassified -> metadata.classify(isBackgroundOnly = true)
        }
        metadataStore.save(persisted)
        userSessionState = Active(metadata)
    }

    private fun currentUserSessionIfClassified(): UserSessionMetadata.Classified? =
        currentUserSession() as? UserSessionMetadata.Classified

    /**
     * Returns true if this user session should continue and false if a new one should be started.
     */
    private fun UserSessionMetadata.Classified.continueUserSession(timestamp: Long, endAppState: AppState): Boolean =
        when {
            // Don't continue session if current time is not congruent with the user session's timestamp
            timestamp < startTimeMs -> {
                logger.trackInternalError(
                    InternalErrorType.ClockBackwardsShift,
                    IllegalStateException("Clock shifted backwards from user session start time.")
                )
                false
            }

            // Don't continue user session if it's background-only and the app is going into the foreground
            isBackgroundOnly && endAppState == AppState.FOREGROUND -> false

            isOverMaxDuration(clock) -> false

            else -> true
        }
}
