package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.arch.SessionType
import io.embrace.android.embracesdk.arch.destination.SessionSpanWriter
import io.embrace.android.embracesdk.arch.destination.SpanAttributeData
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.opentelemetry.embCleanExit
import io.embrace.android.embracesdk.opentelemetry.embColdStart
import io.embrace.android.embracesdk.opentelemetry.embCrashId
import io.embrace.android.embracesdk.opentelemetry.embHeartbeatTimeUnixNano
import io.embrace.android.embracesdk.opentelemetry.embSessionEndType
import io.embrace.android.embracesdk.opentelemetry.embSessionNumber
import io.embrace.android.embracesdk.opentelemetry.embSessionStartType
import io.embrace.android.embracesdk.opentelemetry.embState
import io.embrace.android.embracesdk.opentelemetry.embTerminated
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.session.id.SessionIdTracker
import io.embrace.android.embracesdk.session.lifecycle.ProcessState
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.session.message.PayloadFactory
import java.util.Locale

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
    private val sessionSpanWriter: SessionSpanWriter,
    private val logger: InternalEmbraceLogger
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
                return@transitionState shouldRunOnForeground(state, logger)
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
                    state,
                    logger
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
            },
            crashId = crashId
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
                populateSessionSpanEndAttrs(transitionType.lifeEventType(state), crashId)
                val endMessage = oldSessionAction?.invoke(initial)
                processEndMessage(endMessage, transitionType)
            }

            // third, clean up any previous session state
            boundaryDelegate.prepareForNewSession(clearUserInfo)

            // now, we can start the next session or background activity
            val newState = newSessionAction?.invoke()
            activeSession = newState
            val sessionId = newState?.sessionId
            sessionIdTracker.setActiveSessionId(sessionId, inForeground)
            newState?.let(::populateSessionSpanStartAttrs)

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
                transitionType.name,
                logger
            )

            // et voila! a new session is born
            boundaryDelegate.onSessionStarted(timestamp)
        }
    }

    private fun populateSessionSpanStartAttrs(session: Session) {
        with(sessionSpanWriter) {
            addCustomAttribute(SpanAttributeData(embColdStart.name, session.isColdStart.toString()))
            addCustomAttribute(SpanAttributeData(embSessionNumber.name, session.number.toString()))
            addCustomAttribute(SpanAttributeData(embState.name, session.appState))
            addCustomAttribute(SpanAttributeData(embCleanExit.name, false.toString()))
            session.startType?.toString()?.toLowerCase(Locale.US)?.let {
                addCustomAttribute(SpanAttributeData(embSessionStartType.name, it))
            }
        }
    }

    private fun populateSessionSpanEndAttrs(endType: Session.LifeEventType?, crashId: String?) {
        with(sessionSpanWriter) {
            addCustomAttribute(SpanAttributeData(embCleanExit.name, true.toString()))
            addCustomAttribute(SpanAttributeData(embTerminated.name, false.toString()))
            crashId?.let {
                addCustomAttribute(SpanAttributeData(embCrashId.name, crashId))
            }
            endType?.toString()?.toLowerCase(Locale.US)?.let {
                addCustomAttribute(SpanAttributeData(embSessionEndType.name, it))
            }
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
            ProcessState.FOREGROUND -> scheduleSessionSave(endProcessState, newState)
            ProcessState.BACKGROUND -> scheduleBackgroundActivitySave(endProcessState, newState)
        }
    }

    private fun scheduleSessionSave(
        endProcessState: ProcessState,
        newState: Session
    ) {
        updatePeriodicCacheAttrs()
        periodicSessionCacher.start {
            synchronized(lock) {
                updatePeriodicCacheAttrs()
                payloadFactory.snapshotPayload(endProcessState, clock.now(), newState)?.apply {
                    deliveryService.sendSession(this, SessionSnapshotType.PERIODIC_CACHE)
                }
            }
        }
    }

    private fun scheduleBackgroundActivitySave(endProcessState: ProcessState, initial: Session) {
        updatePeriodicCacheAttrs()
        periodicBackgroundActivityCacher.scheduleSave {
            synchronized(lock) {
                updatePeriodicCacheAttrs()
                payloadFactory.snapshotPayload(endProcessState, clock.now(), initial)?.apply {
                    deliveryService.sendSession(this, SessionSnapshotType.PERIODIC_CACHE)
                }
            }
        }
    }

    private fun updatePeriodicCacheAttrs() {
        val now = clock.now().millisToNanos()
        val attr = SpanAttributeData(embHeartbeatTimeUnixNano.name, now.toString())
        sessionSpanWriter.addCustomAttribute(attr)
        sessionSpanWriter.addCustomAttribute(SpanAttributeData(embTerminated.name, true.toString()))
    }

    private fun logSessionStateChange(
        sessionId: String?,
        timestamp: Long,
        inBackground: Boolean,
        stateChange: String,
        logger: InternalEmbraceLogger
    ) {
        val type = when {
            inBackground -> "background"
            else -> "session"
        }
        logger.logDebug("New session created: ID=$sessionId, timestamp=$timestamp, type=$type, state_change=$stateChange")
    }
}
