package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.opentelemetry.kotlin.error.SdkError
import io.opentelemetry.kotlin.error.SdkErrorHandler

/**
 * Routes failures inside the OpenTelemetry SDK's own code to Embrace's SDK error tracking.
 *
 * API misuse and failures in user-supplied code are deliberately dropped, as they are not defects in
 * the OpenTelemetry SDK. Embrace's own processors track their failures via [ErrorReportingSpanProcessor]
 * and [ErrorReportingLogRecordProcessor] instead.
 */
internal class OtelSdkErrorHandler(
    private val errorHandler: ErrorHandler,
) : SdkErrorHandler {

    override fun onError(error: SdkError) {
        if (error is SdkError.SdkCodeError) {
            errorHandler.trackSdkError(SdkErrorType.OtelSdkCodeError, error.cause)
        }
    }
}
