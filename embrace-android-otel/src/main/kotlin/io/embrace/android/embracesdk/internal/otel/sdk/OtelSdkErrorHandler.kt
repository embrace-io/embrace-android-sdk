package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.opentelemetry.kotlin.error.SdkError
import io.opentelemetry.kotlin.error.SdkErrorHandler

/**
 * Routes failures inside the OpenTelemetry SDK's own code to Embrace's internal error tracking.
 *
 * API misuse and failures in user-supplied code (including Embrace's own processors) are
 * deliberately dropped, as they are not defects in the OpenTelemetry SDK.
 */
internal class OtelSdkErrorHandler(
    private val errorHandler: InternalErrorHandler,
) : SdkErrorHandler {

    override fun onError(error: SdkError) {
        if (error is SdkError.SdkCodeError) {
            errorHandler.trackInternalError(InternalErrorType.OtelSdkCodeError, error.cause)
        }
    }
}
