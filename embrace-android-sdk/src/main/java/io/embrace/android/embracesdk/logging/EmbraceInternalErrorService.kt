package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.payload.LegacyExceptionError
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService

/**
 * Intercepts Embrace SDK's exceptions errors and forwards them to the Embrace API.
 */
internal class EmbraceInternalErrorService(
    private val processStateService: ProcessStateService,
    private val clock: Clock
) : InternalErrorService {

    private var err: LegacyExceptionError = LegacyExceptionError()

    override fun handleInternalError(throwable: Throwable) {
        // if the config service has not been set yet, capture the exception
        if (configService == null || configService?.dataCaptureEventBehavior?.isInternalExceptionCaptureEnabled() == true) {
            err.addException(
                throwable,
                getApplicationState(),
                clock
            )
        }
    }

    override var configService: ConfigService? = null

    override fun getCapturedData(): LegacyExceptionError? = when {
        err.occurrences > 0 -> err
        else -> null
    }

    override fun cleanCollections() {
        err = LegacyExceptionError()
    }

    private fun getApplicationState(): String = when {
        processStateService.isInBackground -> APPLICATION_STATE_BACKGROUND
        else -> APPLICATION_STATE_FOREGROUND
    }

    companion object {

        /**
         * Signals to the API that the application was in the foreground.
         */
        private const val APPLICATION_STATE_FOREGROUND = "foreground"

        /**
         * Signals to the API that the application was in the background.
         */
        private const val APPLICATION_STATE_BACKGROUND = "background"
    }
}
