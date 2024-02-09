package io.embrace.android.embracesdk.arch

import io.embrace.android.embracesdk.config.ConfigListener
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger

/**
 * Orchestrates all data sources that could potentially be used in the SDK. This is a convenient
 * place to coordinate everything in one place.
 */
internal class DataCaptureOrchestrator(
    private val dataSourceState: List<DataSourceState<*, *>>,
    private val logger: InternalEmbraceLogger = InternalStaticEmbraceLogger.logger
) : ConfigListener {

    override fun onConfigChange(configService: ConfigService) {
        dataSourceState.forEach { state ->
            try {
                state.onConfigChange()
            } catch (exc: Throwable) {
                logger.logError("Exception thrown starting data capture", exc)
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
            }
        }
    }
}
