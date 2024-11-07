package io.embrace.android.embracesdk.internal.arch

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
class DataCaptureOrchestrator(
    private val worker: BackgroundWorker,
    private val logger: EmbLogger,
) : EmbraceFeatureRegistry {

    private val dataSourceStates = CopyOnWriteArrayList<DataSourceState<*>>()

    var currentSessionType: SessionType? = null
        set(value) {
            field = value
            onSessionTypeChange()
        }

    override fun add(state: DataSourceState<*>) {
        dataSourceStates.add(state)
        state.dispatchStateChange {
            state.currentSessionType = currentSessionType
        }
    }

    /**
     * Callback that is invoked when the session type changes.
     */
    private fun onSessionTypeChange() {
        dataSourceStates.forEach { state ->
            try {
                // alter the session type - some data sources don't capture for background activities.
                state.dispatchStateChange {
                    state.currentSessionType = currentSessionType
                }
            } catch (exc: Throwable) {
                logger.trackInternalError(InternalErrorType.SESSION_CHANGE_DATA_CAPTURE_FAIL, exc)
            }
        }
    }

    private fun DataSourceState<*>.dispatchStateChange(action: () -> Unit) {
        if (asyncInit) {
            worker.submit(action)
        } else {
            action()
        }
    }
}
