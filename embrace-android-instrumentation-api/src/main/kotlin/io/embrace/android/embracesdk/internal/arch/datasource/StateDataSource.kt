package io.embrace.android.embracesdk.internal.arch.datasource

import androidx.annotation.CallSuper
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.limits.UpToLimitStrategy
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.logging.InternalErrorType

abstract class StateDataSource<T>(
    private val args: InstrumentationArgs,
    defaultValue: T,
    private val schemaTypeFactory: (initialValue: T) -> SchemaType.State<T>,
) : SessionChangeListener, DataSourceImpl(
    args = args,
    limitStrategy = UpToLimitStrategy { MAX_TRANSITIONS }
) {
    private var currentState: T = defaultValue
    private var sessionStateToken: SessionStateToken<T>? = null

    protected fun onStateChange(updateDetectedTimeMs: Long, newState: T) {
        val oldState = currentState
        currentState = newState
        val currentStateToken = sessionStateToken
        if (currentStateToken != null) {
            captureTelemetry(inputValidation = { newState != oldState }) {
                currentStateToken.update(
                    updateDetectedTimeMs = updateDetectedTimeMs,
                    newValue = newState
                )
            }
        }
    }

    @CallSuper
    override fun resetDataCaptureLimits() {
        super.resetDataCaptureLimits()
        createSessionStateSpan(currentState)
    }

    @CallSuper
    override fun onPostSessionChange() {
        sessionStateToken?.end()
    }

    private fun createSessionStateSpan(initialValue: T) {
        try {
            sessionStateToken = args.destination.startSessionStateCapture(
                state = schemaTypeFactory(initialValue)
            )
        } catch (t: Throwable) {
            logger.trackInternalError(InternalErrorType.SESSION_STATE_CREATION_FAIL, t)
        }
    }
}

private const val MAX_TRANSITIONS = 1000
