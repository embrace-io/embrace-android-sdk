package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.opentelemetry.kotlin.error.SdkError
import io.opentelemetry.kotlin.error.SdkErrorHandler
import io.opentelemetry.kotlin.error.setProcessSdkErrorHandler
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    override fun onError(error: SdkError) = errorHandler.trackOtelSdkError(error)
}

/**
 * The only handler Embrace passes to opentelemetry-kotlin's [setProcessSdkErrorHandler]. Registered once per
 * process, it forwards to the [ErrorHandler] of the running SDK instance.
 */
object ProcessOtelSdkErrorHandler : SdkErrorHandler {

    private val registered = AtomicBoolean(false)
    private val target = AtomicReference<ErrorHandler?>(null)

    /**
     * Forwards errors to [errorHandler], registering with opentelemetry-kotlin on the first call.
     */
    fun attach(errorHandler: ErrorHandler) {
        target.set(errorHandler)
        if (registered.compareAndSet(false, true)) {
            setProcessSdkErrorHandler(this)
        }
    }

    /**
     * Stops forwarding errors to [errorHandler] if it is the current target.
     */
    fun detach(errorHandler: ErrorHandler) {
        target.compareAndSet(errorHandler, null)
    }

    override fun onError(error: SdkError) {
        target.get()?.trackOtelSdkError(error)
    }
}

internal fun ErrorHandler.trackOtelSdkError(error: SdkError) {
    if (error is SdkError.SdkCodeError) {
        trackSdkError(SdkErrorType.OtelSdkCodeError, error.cause)
    }
}
