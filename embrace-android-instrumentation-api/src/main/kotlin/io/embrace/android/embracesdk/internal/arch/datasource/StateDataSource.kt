package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Base [DataSource] to handle State updates in a unified way. This will create the right objects to represent a state in the data model,
 * as well as track and put in the common metadata expected by the backend.
 *
 * When state capture begins, a span will be cut for each session part that it overlaps with. By default, state capture begins when the
 * data source is created, but it can be deferred to when [onStateChange] is first called if [captureStateOnCreation] is overridden and
 * set to false.
 *
 * When state changes are journaled, no span is cut. Every change of value reported to [onStateChange] is emitted on the event
 * bus as a [StateUpdate] instead, for whatever records them to order against the updates of every other state, and session
 * parts are not this data source's concern.
 */
abstract class StateDataSource<T : Any>(
    private val args: InstrumentationArgs,
    private val stateTypeFactory: (initialValue: T) -> SchemaType.State<T>,
    defaultValue: T,
    maxTransitions: Int = DEFAULT_MAX_TRANSITIONS,
) : SessionPartEndListener, SessionPartChangeListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { maxTransitions },
    instrumentationName = "${stateTypeFactory(defaultValue).stateName}_state_data_source",
) {
    private val stateName: String = stateTypeFactory(defaultValue).stateName

    val stateAttributeKey: String = "emb.state.$stateName"

    /**
     * Attribute key under which the type of the current state value is stored when the state value is a system value.
     */
    val stateValueTypeAttributeKey: String = "$stateAttributeKey.value_type"

    /**
     * If true, state capture will begin when [onDataCaptureEnabled] is called. Otherwise, it will happen lazily when [onStateChange]
     * is first invoked using the new value as the initial state.
     */
    protected open val captureStateOnCreation: Boolean = true

    /**
     * Whether state changes are journaled, which needs a session part's telemetry to be persisted as multiple files.
     */
    private val journaled: Boolean by lazy {
        with(args.configService.config) { autoDataCapture.sharedStateSpanEnabled && persistence.multiFileEnabled }
    }

    /**
     * Held while a journaled update replaces the state's value and is emitted, so that updates reported from several threads
     * are emitted in the order they replaced the value, and the last one emitted is the value the state holds.
     */
    private val journalLock = Any()

    /**
     * Transitions that were not emitted since the last update that was, guarded by [journalLock]: those the instrumentation
     * said it dropped, and updates that did not change the value.
     */
    private var unreportedTransitions = 0

    private val currentState: AtomicReference<T> = AtomicReference(defaultValue)
    private val partStateToken: AtomicReference<SessionPartStateToken<T>?> = AtomicReference()
    private val unrecordedTransitions = AtomicReference(noUnrecordedTransitions)
    private val stateCaptureActive = AtomicBoolean(false)

    /**
     * Held while state capture begins and while a part change takes or starts the part's state span. The first capture can
     * run on any thread, as an async source's does, so this orders it against a part change: either it begins capture first
     * and the part change then ends its span and starts the next part's, or the part change goes first and the first capture
     * starts the one span for the new part.
     */
    private val partStateLock = Any()

    /**
     * Whether a part change has ended the part's state span and not yet started the next part's, guarded by [partStateLock].
     * The ending part is still the active one meanwhile, so a first capture then leaves the next part's span to the part change
     * rather than start one for the part that is ending.
     */
    private var partEnding = false

    /**
     * Notify that the state has changed at the given time to the given value, with the given number of transitions dropped by the
     * instrumentation since the last time this data source was notified of a state change.
     */
    fun onStateChange(
        newState: T,
        transitionTimeMs: Long,
        transitionAttributes: Map<String, String> = emptyMap(),
        droppedTransitions: Int = 0,
    ) {
        if (journaled) {
            journalUpdate(newState, transitionTimeMs, transitionAttributes, droppedTransitions)
            return
        }
        if (!stateCaptureActive.get()) {
            startStateCapture()
        }

        val oldState = currentState.getAndSet(newState)
        val outgoingAttributes = if (newState != oldState) outgoingValueAttributes(oldState) else emptyMap()
        val currentStateToken = partStateToken.get()
        // Track the number of transitions dropped by instrumentation that didn't cause this to be invoked
        unrecordedTransitions.updateDroppedByInstrumentation(droppedTransitions)
        if (currentStateToken != null) {
            var droppedByLimit = newState != oldState
            captureTelemetry(
                inputValidation = { newState != oldState },
                invalidInputCallback = {
                    // Input validation failing means the transition didn't change the state value, so keep track of it as unrecorded
                    unrecordedTransitions.updateDroppedByInstrumentation(1)
                },
            ) {
                droppedByLimit = false
                val droppedTransitions = unrecordedTransitions.getAndSet(noUnrecordedTransitions)
                val transitionRecorded = currentStateToken.update(
                    newValue = newState,
                    transitionTimeMs = transitionTimeMs,
                    transitionAttributes = if (outgoingAttributes.isEmpty()) {
                        transitionAttributes
                    } else {
                        outgoingAttributes + transitionAttributes
                    },
                    unrecordedTransitions = droppedTransitions,
                )
                // If the transition was not recorded by the token, it means the associated session has ended.
                // Add that transition as occurring outside of a session and also add back unrecorded transitions we tried to record.
                if (!transitionRecorded) {
                    unrecordedTransitions.incrementCount(
                        notInSession = droppedTransitions.notInSession + 1,
                        droppedByInstrumentation = droppedTransitions.droppedByInstrumentation,
                    )
                }
            }
            // A valid transition that never reached the action was refused by the limit strategy
            if (droppedByLimit) {
                unrecordedTransitions.updateDroppedByInstrumentation(1)
            }
        } else {
            // The token not existing means a session hasn't been created yet
            unrecordedTransitions.updateNotInSession(1)
        }
    }

    /**
     * Return the current state value
     */
    fun getCurrentStateValue(): T = currentState.get()

    /**
     * Return the attributes that represent the current state value, i.e. the literal value and type, if applicable.
     */
    fun currentStateAttributes(): Map<String, Any> {
        val value = currentState.get()
        return buildMap {
            put(stateAttributeKey, value)
            SchemaType.State.valueType(value)?.let { valueType ->
                put(stateValueTypeAttributeKey, valueType)
            }
        }
    }

    /**
     * Returns true if the data source is currently active
     */
    fun isActive(): Boolean = stateCaptureActive.get()

    /**
     * Attributes describing the state [value] being left, added to the transition away from it.
     */
    protected open fun outgoingValueAttributes(value: T): Map<String, String> = emptyMap()

    @CallSuper
    override fun onDataCaptureEnabled() {
        if (captureStateOnCreation) {
            startStateCapture()
        }
    }

    @CallSuper
    override fun onPreSessionEnd() {
        if (journaled) {
            return
        }
        synchronized(partStateLock) {
            partEnding = true
            partStateToken.getAndSet(null)
        }?.apply {
            end(unrecordedTransitions.getAndSet(noUnrecordedTransitions))
        }
    }

    @CallSuper
    override fun onPostSessionChange() {
        if (journaled) {
            return
        }
        synchronized(partStateLock) {
            partEnding = false
            createSessionStateSpan(currentState.get())
        }
    }

    private fun startStateCapture() {
        if (journaled) {
            // the value the state starts with is its first update
            synchronized(journalLock) {
                if (stateCaptureActive.compareAndSet(false, true)) {
                    args.eventBus.emit(StateUpdate.KEY, StateUpdate(stateName, currentState.get(), clock.now()))
                }
            }
            return
        }
        synchronized(partStateLock) {
            if (stateCaptureActive.compareAndSet(false, true) && !partEnding) {
                createSessionStateSpan(currentState.get())
            }
        }
    }

    /**
     * Emits [newState] as an update if it changes the state's value, or is the first value reported. Where it falls among the
     * changes of every state is decided by whatever records the updates.
     */
    @OptIn(ExperimentalSemconv::class)
    private fun journalUpdate(
        newState: T,
        transitionTimeMs: Long,
        transitionAttributes: Map<String, String>,
        droppedTransitions: Int,
    ) {
        synchronized(journalLock) {
            val firstUpdate = stateCaptureActive.compareAndSet(false, true)
            val oldState = currentState.getAndSet(newState)
            unreportedTransitions += droppedTransitions
            if (!firstUpdate && newState == oldState) {
                unreportedTransitions++
                return
            }
            val attributes = buildMap {
                // first, so that it is not the attribute cut if the change has more than it can carry
                if (unreportedTransitions > 0) {
                    put(EmbStateTransitionAttributes.EMB_STATE_DROPPED_BY_INSTRUMENTATION, unreportedTransitions.toString())
                }
                if (!firstUpdate) {
                    putAll(outgoingValueAttributes(oldState))
                }
                putAll(transitionAttributes)
            }
            unreportedTransitions = 0
            args.eventBus.emit(StateUpdate.KEY, StateUpdate(stateName, newState, transitionTimeMs, attributes))
        }
    }

    private fun createSessionStateSpan(initialValue: T) {
        if (stateCaptureActive.get() && args.sessionPartId() != null) {
            errorHandler.guardSdkCode(SdkErrorType.SessionStateCreationFail) {
                val token = args.destination.startSessionPartStateCapture(
                    state = stateTypeFactory(initialValue),
                )
                // a part change normally finds the previous part's span already taken, but one still open must not be left in
                // progress for the rest of the process
                partStateToken.getAndSet(token)?.end()
            }
        }
    }

    private fun AtomicReference<UnrecordedTransitions>.updateNotInSession(count: Int) {
        incrementCount(notInSession = count)
    }

    private fun AtomicReference<UnrecordedTransitions>.updateDroppedByInstrumentation(count: Int) {
        incrementCount(droppedByInstrumentation = count)
    }

    private fun AtomicReference<UnrecordedTransitions>.incrementCount(notInSession: Int = 0, droppedByInstrumentation: Int = 0) {
        while (true) { // CAS loop
            val oldValue = get()
            val replacement = UnrecordedTransitions(
                notInSession = oldValue.notInSession + notInSession,
                droppedByInstrumentation = oldValue.droppedByInstrumentation + droppedByInstrumentation,
            )

            if (compareAndSet(oldValue, replacement)) {
                return
            }
        }
    }
}

/**
 * Maximum transitions per state span that the backend can expect. This could be overridable in the future, at which point the max value
 * should be encoded in the data.
 */
private const val DEFAULT_MAX_TRANSITIONS = 100
