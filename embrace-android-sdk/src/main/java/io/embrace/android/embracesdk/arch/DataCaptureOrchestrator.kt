package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.capture.internal.errors.InternalErrorType
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.EmbLogger

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
internal class DataCaptureOrchestrator(
    private val dataSourceState: List<DataSourceState<*>>,
    private val logger: EmbLogger,
    configService: ConfigService
) {

    init {
        configService.addListener {
            onConfigChange()
        }
    }

    private fun onConfigChange() {
        dataSourceState.forEach { state ->
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
    fun onSessionTypeChange(sessionType: SessionType) {
        dataSourceState.forEach { state ->
            try {
                // alter the session type - some data sources don't capture for background activities.
                state.onSessionTypeChange(sessionType)
            } catch (exc: Throwable) {
                logger.logError("Exception thrown starting data capture", exc)
                logger.trackInternalError(InternalErrorType.SESSION_CHANGE_DATA_CAPTURE_FAIL, exc)
            }
        }
    }
}
