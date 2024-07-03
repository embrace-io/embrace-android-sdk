package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.EmbLogger
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
internal class DataCaptureOrchestrator(
    configService: ConfigService,
    private val logger: EmbLogger
) : EmbraceFeatureRegistry {

    init {
        configService.addListener {
            onConfigChange()
        }
    }

    private val dataSourceStates = CopyOnWriteArrayList<DataSourceState<*>>()

    var currentSessionType: SessionType? = null
        set(value) {
            field = value
            onSessionTypeChange()
        }

    override fun add(state: DataSourceState<*>) {
        dataSourceStates.add(state)
        state.currentSessionType = currentSessionType
    }

    private fun onConfigChange() {
        dataSourceStates.forEach { state ->
            try {
                state.onConfigChange()
            } catch (exc: Throwable) {
                logger.logError("Exception thrown starting data capture", exc)
                logger.trackInternalError(InternalErrorType.CFG_CHANGE_DATA_CAPTURE_FAIL, exc)
            }
        }
    }

    /**
     * Callback that is invoked when the session type changes.
     */
    private fun onSessionTypeChange() {
        dataSourceStates.forEach { state ->
            try {
                // alter the session type - some data sources don't capture for background activities.
                state.currentSessionType = currentSessionType
            } catch (exc: Throwable) {
                logger.logError("Exception thrown starting data capture", exc)
                logger.trackInternalError(InternalErrorType.SESSION_CHANGE_DATA_CAPTURE_FAIL, exc)
            }
        }
    }
}
