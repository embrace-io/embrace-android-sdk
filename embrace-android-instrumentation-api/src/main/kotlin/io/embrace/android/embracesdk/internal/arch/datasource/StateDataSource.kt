package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionEndListener
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.concurrent.atomic.AtomicReference

/**
 * Base [DataSource] to handle State updates in a unified way. This will create the right objects to represent a state in the data model,
 * as well as track and put in the common metadata expected by the backend.
 */
abstract class StateDataSource<T>(
    private val args: InstrumentationArgs,
    private val stateValueFactory: (initialValue: T) -> SchemaType.State<T>,
    defaultValue: T,
    maxTransitions: Int = DEFAULT_MAX_TRANSITIONS,
) : SessionEndListener, SessionChangeListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { maxTransitions }
) {
    private val currentState: AtomicReference<T> = AtomicReference(defaultValue)
    private val sessionStateToken: AtomicReference<SessionStateToken<T>?> = AtomicReference()
    private val unrecordedTransitions = AtomicReference(noUnrecordedTransitions)

    fun onStateChange(updateDetectedTimeMs: Long, newState: T, droppedTransitions: Int = 0) {
        val oldState = currentState.getAndSet(newState)
        val currentStateToken = sessionStateToken.get()
        // Track the number of transitions dropped by instrumentation that didn't cause this to be invoked
        unrecordedTransitions.updateDroppedByInstrumentation(droppedTransitions)
        if (currentStateToken != null) {
            captureTelemetry(
                inputValidation = { newState != oldState },
                invalidInputCallback = {
                    // Input validation failing means the transition didn't change the state value, so keep track of it as unrecorded
                    unrecordedTransitions.updateDroppedByInstrumentation(1)
                }
            ) {
                val droppedTransitions = unrecordedTransitions.getAndSet(noUnrecordedTransitions)
                val transitionRecorded = currentStateToken.update(
                    updateDetectedTimeMs = updateDetectedTimeMs,
                    newValue = newState,
                    unrecordedTransitions = droppedTransitions
                )

                // If the transition was not recorded by the token, it means the associated session has ended.
                // Add that transition as occurring outside of a session and also add back unrecorded transitions we tried to record.
                if (!transitionRecorded) {
                    unrecordedTransitions.incrementCount(
                        notInSession = droppedTransitions.notInSession + 1,
                        droppedByInstrumentation = droppedTransitions.droppedByInstrumentation
                    )
                }
            }
        } else {
            // The token not existing means a session hasn't be creat yet
            unrecordedTransitions.updateNotInSession(1)
        }
    }

    @CallSuper
    override fun onDataCaptureEnabled() {
        // Create a new state span as soon as the data source is enabled if there's an active session
        if (args.sessionId() != null) {
            createSessionStateSpan(currentState.get())
        }
    }

    @CallSuper
    override fun onPreSessionEnd() {
        sessionStateToken.getAndSet(null)?.apply {
            end(unrecordedTransitions.get())
        }
    }

    @CallSuper
    override fun onPostSessionChange() {
        createSessionStateSpan(currentState.get())
    }

    private fun createSessionStateSpan(initialValue: T) {
        try {
            sessionStateToken.set(
                args.destination.startSessionStateCapture(
                    state = stateValueFactory(initialValue)
                )
            )
        } catch (t: Throwable) {
            logger.trackInternalError(InternalErrorType.SESSION_STATE_CREATION_FAIL, t)
        }
    }

    private fun AtomicReference<UnrecordedTransitions>.updateNotInSession(count: Int) {
        incrementCount(notInSession = count)
    }

    private fun AtomicReference<UnrecordedTransitions>.updateDroppedByInstrumentation(count: Int) {
        incrementCount(droppedByInstrumentation = count)
    }

    private fun AtomicReference<UnrecordedTransitions>.incrementCount(notInSession: Int = 0, droppedByInstrumentation: Int = 0) {
        synchronized(this) {
            set(
                get().let { oldValue ->
                    UnrecordedTransitions(
                        notInSession = oldValue.notInSession + notInSession,
                        droppedByInstrumentation = oldValue.droppedByInstrumentation + droppedByInstrumentation
                    )
                }
            )
        }
    }
}

/**
 * Maximum transitions per state span that the backend can expect. This could be overridable in the future, at which point the max value
 * should be encoded in the data.
 */
private const val DEFAULT_MAX_TRANSITIONS = 100
