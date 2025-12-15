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
) : SessionEndListener, SessionChangeListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { MAX_TRANSITIONS }
) {
    private val currentState: AtomicReference<T> = AtomicReference(defaultValue)
    private val sessionStateToken: AtomicReference<SessionStateToken<T>?> = AtomicReference()
    private val unrecordedTransitions = AtomicReference(noUnrecordedTransitions)

    protected fun onStateChange(updateDetectedTimeMs: Long, newState: T, droppedTransitions: Int = 0) {
        val oldState = currentState.getAndSet(newState)
        val currentStateToken = sessionStateToken.get()
        unrecordedTransitions.updateDroppedByInstrumentation(droppedTransitions)
        if (currentStateToken != null) {
            captureTelemetry(
                inputValidation = { newState != oldState },
                invalidInputCallback = {
                    unrecordedTransitions.updateDroppedByInstrumentation(1)
                }
            ) {
                currentStateToken.update(
                    updateDetectedTimeMs = updateDetectedTimeMs,
                    newValue = newState,
                    unrecordedTransitions = unrecordedTransitions.getAndSet(noUnrecordedTransitions)
                )
            }
        } else {
            unrecordedTransitions.dropTransitionNotInSession()
        }
    }

    @CallSuper
    override fun onPreSessionEnd() {
        sessionStateToken.getAndSet(null)?.apply {
            end()
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

    private fun AtomicReference<UnrecordedTransitions>.dropTransitionNotInSession() {
        getAndUpdate { oldValue ->
            oldValue.copy(
                notInSession = oldValue.notInSession + 1,
                droppedByInstrumentation = oldValue.droppedByInstrumentation
            )
        }
    }

    private fun AtomicReference<UnrecordedTransitions>.updateDroppedByInstrumentation(droppedTransitions: Int) {
        getAndUpdate { oldValue ->
            oldValue.copy(
                notInSession = oldValue.notInSession,
                droppedByInstrumentation = oldValue.droppedByInstrumentation + droppedTransitions
            )
        }
    }
}

/**
 * Maximum transitions per state span that the backend can expect. This could be overridable in the future, at which point the max value
 * should be encoded in the data.
 */
private const val MAX_TRANSITIONS = 1000
