package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.payload.LegacyExceptionError

/**
 * Reports an internal error to Embrace. An internal error is defined as an exception that was
 * caught within Embrace code & logged to [EmbLogger].
 */
internal interface InternalErrorService {
    fun setConfigService(configService: ConfigService?)
    fun handleInternalError(throwable: Throwable)
    fun resetExceptionErrorObject()
    val currentExceptionError: LegacyExceptionError?
}
