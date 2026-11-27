package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionPartChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionPartEndListener
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue
import io.embrace.android.embracesdk.internal.config.isAppStateEnabled
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.state.StateDeclaration
import io.embrace.android.embracesdk.internal.state.StateDefinition
import io.embrace.android.embracesdk.internal.state.StateDrops
import io.embrace.android.embracesdk.internal.state.StateEvents
import io.embrace.android.embracesdk.internal.state.StateUpdate
import io.embrace.android.embracesdk.internal.state.StateValues
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
 * When the app state data model is on, none of that happens here. This data source then only defines the state, by its name, its
 * limit on changes ([maxTransitions]) and the attribute keys a change of it may carry ([changeAttributeKeys]), and reports each
 * value over the event bus to the one recorder, which puts every change of every state in one order and enforces the limit. The
 * state has a value from the moment this data source is enabled, whatever [captureStateOnCreation] says.
 *
 * A state has one reporter, which is its data source. A report from anywhere else, such as a hosted SDK's, arrives on the event
 * bus and is passed on from here like any other, under the same lock. So the latest report stands whoever made it, and whether
 * a report is a change is settled by one rule, [StateValues.isRepeat], against what this data source last handed the recorder.
 */
abstract class StateDataSource<T : Any>(
    private val args: InstrumentationArgs,
    private val stateTypeFactory: (initialValue: T) -> SchemaType.State<T>,
    defaultValue: T,
    maxTransitions: Int = DEFAULT_MAX_TRANSITIONS,
    changeAttributeKeys: Set<String> = emptySet(),
) : SessionPartEndListener, SessionPartChangeListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { maxTransitions },
    instrumentationName = "${stateTypeFactory(defaultValue).stateName}_state_data_source",
) {
    private val stateName: String = stateTypeFactory(defaultValue).stateName

    val stateAttributeKey: String = "emb.state.$stateName"

    /**
     * What this data source declares to the recorder when the app state data model is on, or null when it is off.
     */
    private val stateDefinition: StateDefinition? = if (args.configService.isAppStateEnabled()) {
        StateDefinition(stateName, maxTransitions, changeAttributeKeys)
    } else {
        null
    }

    /**
     * Whether every value is reported from the moment this data source is enabled, a default or an unknown one included, as the app
     * state data model has it. Otherwise a data source may leave a state out until it has something to say.
     */
    protected val reportsEveryValue: Boolean = stateDefinition != null

    /**
     * Whether this data source has declared its state to the recorder, which it does as it is enabled.
     */
    @Volatile
    private var declared = false

    /**
     * Held while a value is handed to the recorder. See [report].
     */
    private val reportLock = Any()

    /**
     * What this data source last handed the recorder, as the recorder holds it: the text once cut to length, and whether the SDK
     * generated it. Null until the state is declared. Guarded by [reportLock].
     */
    private var reportedValue: String? = null
    private var reportedGenerated = false

    /**
     * Whether what was last handed over came from outside this data source, so that no value of [T] stands for it. Guarded by
     * [reportLock].
     */
    private var reportedFromOutside = false

    /**
     * Attribute key under which the type of the current state value is stored when the state value is a system value.
     */
    val stateValueTypeAttributeKey: String = "$stateAttributeKey.value_type"

    /**
     * If true, state capture will begin when [onDataCaptureEnabled] is called. Otherwise, it will happen lazily when [onStateChange]
     * is first invoked using the new value as the initial state.
     */
    protected open val captureStateOnCreation: Boolean = true

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
        if (stateDefinition != null) {
            reportToRecorder(newState, transitionTimeMs, transitionAttributes, droppedTransitions)
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
        if (stateDefinition != null) {
            // the recorder's values are what a log is stamped with
            return emptyMap()
        }
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
    protected open fun outgoingValueAttributes(
        value: T,
    ): Map<String, String> = outgoingAttributes(value is SystemStateValue)

    /**
     * Attributes describing the value being left when all that is known of it is whether the SDK [generated] it, as when it was
     * reported from outside this data source. A data source whose attributes do not depend on the value itself overrides this
     * one, and [outgoingValueAttributes] then follows it.
     */
    protected open fun outgoingAttributes(generated: Boolean): Map<String, String> = emptyMap()

    @CallSuper
    override fun onDataCaptureEnabled() {
        if (stateDefinition != null) {
            if (!StateDefinition.isValidName(stateName)) {
                // the recorder would refuse the declaration, and every report after it, without a word: say so once, here,
                // where whoever adds a state will see it
                errorHandler.trackSdkError(
                    SdkErrorType.DataSourceDataCaptureFail,
                    IllegalArgumentException("'$stateName' cannot name a state: lower-case letters, digits and hyphens only"),
                )
                return
            }
            synchronized(reportLock) {
                // a value reported before this point is the one the state is declared with, so that the recorder starts from
                // what this data source last reported
                val firstValue = currentState.get()
                val generated = firstValue is SystemStateValue
                args.eventBus.emit(
                    StateEvents.DECLARED,
                    StateDeclaration(stateDefinition, firstValue.toString(), generated, clock.now()),
                )
                reportedValue = StateValues.cap(firstValue.toString())
                reportedGenerated = generated
                reportedFromOutside = false
                declared = true
            }
            // a report of this state from outside, such as a hosted SDK's, is passed on from here, so that the state has one
            // reporter and the latest report stands whoever made it
            args.eventBus.addHandler(StateEvents.REPORTED) { outside ->
                if (outside.state == stateName) {
                    report(outside.value, false, null, outside.timeMs, emptyMap(), 0)
                }
            }
        } else if (captureStateOnCreation) {
            startStateCapture()
        }
    }

    /**
     * Reports [newState] to the recorder, with the attributes of the value being left when it is a change.
     */
    private fun reportToRecorder(
        newState: T,
        transitionTimeMs: Long,
        transitionAttributes: Map<String, String>,
        droppedTransitions: Int,
    ) = report(
        value = newState.toString(),
        generated = newState is SystemStateValue,
        typed = newState,
        timeMs = transitionTimeMs,
        transitionAttributes = transitionAttributes,
        droppedTransitions = droppedTransitions,
    )

    /**
     * Hands [value] to the recorder if it is a change from what this data source last handed it, with the attributes of the value
     * being left. [typed] is the value as this data source knows it, or null for a report from outside.
     *
     * Whether it is a change is decided by the rule the recorder decides by, against the same value, so that what describes the
     * value being left is gathered once, for a change, and with nothing of this data source's running under the recorder's lock.
     */
    private fun report(
        value: String,
        generated: Boolean,
        typed: T?,
        timeMs: Long,
        transitionAttributes: Map<String, String>,
        droppedTransitions: Int,
    ) {
        errorHandler.guardSdkCode(SdkErrorType.DataSourceDataCaptureFail) {
            // held so that every report of this state, its own or from outside, is handed to the recorder in the order this data
            // source took it in. The recorder is what orders changes across states, and never calls back into a data source.
            synchronized(reportLock) {
                val leftTyped = currentState.get()
                if (typed != null) {
                    currentState.set(typed)
                }
                if (!declared) {
                    // the recorder discards an update for a state it has not been told of. A value of this data source's own is
                    // kept above to declare the state with, and a report from outside has no state to go to yet.
                    return
                }
                if (droppedTransitions > 0) {
                    args.eventBus.emit(StateEvents.DROPPED, StateDrops(stateName, droppedTransitions))
                }
                val capped = StateValues.cap(value)
                val held = reportedValue
                if (held != null && StateValues.isRepeat(held, reportedGenerated, capped, generated)) {
                    return
                }
                val leaving = if (reportedFromOutside) outgoingAttributes(reportedGenerated) else outgoingValueAttributes(leftTyped)
                reportedValue = capped
                reportedGenerated = generated
                reportedFromOutside = typed == null
                args.eventBus.emit(
                    StateEvents.UPDATED,
                    StateUpdate(stateName, capped, generated, timeMs, leaving + transitionAttributes),
                )
            }
        }
    }

    @CallSuper
    override fun onPreSessionEnd() {
        if (stateDefinition != null) {
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
        if (stateDefinition != null) {
            return
        }
        synchronized(partStateLock) {
            partEnding = false
            createSessionStateSpan(currentState.get())
        }
    }

    private fun startStateCapture() {
        synchronized(partStateLock) {
            if (stateCaptureActive.compareAndSet(false, true) && !partEnding) {
                createSessionStateSpan(currentState.get())
            }
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
