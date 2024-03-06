package io.embrace.android.embracesdk.logging

import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.payload.LegacyExceptionError

internal interface InternalErrorService {
    fun setConfigService(configService: ConfigService?)
    fun handleInternalError(throwable: Throwable)
    fun resetExceptionErrorObject()
    val currentExceptionError: LegacyExceptionError?
}
