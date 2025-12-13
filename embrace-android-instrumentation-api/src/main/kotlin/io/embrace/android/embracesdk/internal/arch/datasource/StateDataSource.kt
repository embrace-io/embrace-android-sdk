package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.SessionEndListener
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    private val unrecordedTransitions = AtomicInteger(0)

    protected fun onStateChange(updateDetectedTimeMs: Long, newState: T, droppedTransitions: Int = 0) {
        val oldState = currentState.getAndSet(newState)
        val currentStateToken = sessionStateToken.get()
        unrecordedTransitions.addAndGet(droppedTransitions)
        var transitionRecorded = false
        if (currentStateToken != null) {
            captureTelemetry(inputValidation = { newState != oldState }) {
                currentStateToken.update(
                    updateDetectedTimeMs = updateDetectedTimeMs,
                    newValue = newState,
                    droppedTransitions = unrecordedTransitions.getAndSet(0)
                )
                transitionRecorded = true
            }
        }

        if (!transitionRecorded) {
            unrecordedTransitions.incrementAndGet()
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
}

private const val MAX_TRANSITIONS = 1000
