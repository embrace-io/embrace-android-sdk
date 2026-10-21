package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSpan
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ErrorReportingSpanProcessorTest {

    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)
    private val span = FakeReadWriteSpan(FakeSpan(name = "span"))

    @Test
    fun `calls are forwarded`() {
        val delegate = FakeSpanProcessor()
        val processor = ErrorReportingSpanProcessor(delegate, errorHandler)
        processor.onStart(span, NoopOpenTelemetry.context.implicit())
        processor.onEnding(span)
        processor.onEnd(span)
        assertEquals(listOf("span"), delegate.startedSpanNames)
        assertEquals(listOf("span"), delegate.endingSpanNames)
        assertEquals(listOf("span"), delegate.endedSpanNames)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `onStart failure is tracked`() {
        val cause = IllegalStateException("boom")
        val processor = ErrorReportingSpanProcessor(FakeSpanProcessor(onStartAction = { throw cause }), errorHandler)
        processor.onStart(span, NoopOpenTelemetry.context.implicit())
        assertTracked(cause)
    }

    @Test
    fun `onEnd failure is tracked`() {
        val cause = IllegalStateException("boom")
        val processor = ErrorReportingSpanProcessor(FakeSpanProcessor(onEndAction = { throw cause }), errorHandler)
        processor.onEnd(span)
        assertTracked(cause)
    }

    private fun assertTracked(cause: Throwable) {
        val msg = errorHandler.sdkErrorMessages.single()
        assertEquals(SdkErrorType.OtelProcessorFail.toString(), msg.msg)
        assertSame(cause, msg.throwable)
    }
}
