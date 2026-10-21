package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ErrorReportingLogRecordProcessorTest {

    private val errorHandler = FakeErrorHandler(throwOnSdkError = false)

    @Test
    fun `onEmit is forwarded`() {
        val delegate = FakeLogRecordProcessor()
        val processor = ErrorReportingLogRecordProcessor(delegate, errorHandler)
        processor.onEmit(FakeReadWriteLogRecord(body = "log"), NoopOpenTelemetry.context.implicit())
        assertEquals(listOf("log"), delegate.processedLogBodies)
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `onEmit failure is tracked`() {
        val cause = IllegalStateException("boom")
        val processor = ErrorReportingLogRecordProcessor(FakeLogRecordProcessor { throw cause }, errorHandler)
        processor.onEmit(FakeReadWriteLogRecord(body = "log"), NoopOpenTelemetry.context.implicit())

        val msg = errorHandler.sdkErrorMessages.single()
        assertEquals(SdkErrorType.OtelProcessorFail.toString(), msg.msg)
        assertSame(cause, msg.throwable)
    }
}
