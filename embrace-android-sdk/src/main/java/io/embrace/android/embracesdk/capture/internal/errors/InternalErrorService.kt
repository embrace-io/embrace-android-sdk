package io.embrace.android.embracesdk.capture.internal.errors

import io.embrace.android.embracesdk.arch.DataCaptureService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.payload.LegacyExceptionError

/**
 * Reports an internal error to Embrace. An internal error is defined as an exception that was
 * caught within Embrace code & logged to [EmbLogger].
 */
internal interface InternalErrorService : DataCaptureService<LegacyExceptionError?> {
    var configService: ConfigService?
    fun handleInternalError(throwable: Throwable)
}
