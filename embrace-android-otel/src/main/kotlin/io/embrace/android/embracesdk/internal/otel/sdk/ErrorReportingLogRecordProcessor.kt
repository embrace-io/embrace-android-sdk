package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

/**
 * Wraps Embrace's own [LogRecordProcessor] so its failures are tracked as SDK errors. The OpenTelemetry SDK
 * reports a throwing processor as user code, which [OtelSdkErrorHandler] drops.
 */
internal class ErrorReportingLogRecordProcessor(
    private val delegate: LogRecordProcessor,
    private val errorHandler: ErrorHandler,
) : LogRecordProcessor by delegate {

    override fun onEmit(log: ReadWriteLogRecord, context: Context) {
        errorHandler.guardSdkCode(SdkErrorType.OtelProcessorFail) {
            delegate.onEmit(log, context)
        }
    }
}
