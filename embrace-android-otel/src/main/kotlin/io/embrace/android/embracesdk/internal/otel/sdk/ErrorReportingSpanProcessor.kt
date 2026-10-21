package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan

/**
 * Wraps Embrace's own [SpanProcessor] so its failures are tracked as SDK errors. The OpenTelemetry SDK
 * reports a throwing processor as user code, which [OtelSdkErrorHandler] drops.
 */
internal class ErrorReportingSpanProcessor(
    private val delegate: SpanProcessor,
    private val errorHandler: ErrorHandler,
) : SpanProcessor by delegate {

    override fun onStart(span: ReadWriteSpan, parentContext: Context) = guard {
        delegate.onStart(span, parentContext)
    }

    override fun onEnding(span: ReadWriteSpan) = guard {
        delegate.onEnding(span)
    }

    override fun onEnd(span: ReadableSpan) = guard {
        delegate.onEnd(span)
    }

    private inline fun guard(action: () -> Unit) {
        errorHandler.guardSdkCode(SdkErrorType.OtelProcessorFail) { action() }
    }
}
