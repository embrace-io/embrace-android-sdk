package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.opentelemetry.kotlin.error.SdkError
import io.opentelemetry.kotlin.error.SdkErrorHandler
import io.opentelemetry.kotlin.error.SdkErrorSeverity
import io.opentelemetry.kotlin.error.setProcessSdkErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OtelSdkErrorHandlerTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var handler: OtelSdkErrorHandler

    @Before
    fun setUp() {
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        handler = OtelSdkErrorHandler(errorHandler)
    }

    @Test
    fun `sdk code error is tracked`() {
        val cause = IllegalStateException("boom")
        handler.onError(SdkError.SdkCodeError(cause, "failed", SdkErrorSeverity.ERROR))

        val msg = errorHandler.sdkErrorMessages.single()
        assertEquals(SdkErrorType.OtelSdkCodeError.toString(), msg.msg)
        assertSame(cause, msg.throwable)
    }

    @Test
    fun `user code error is ignored`() {
        handler.onError(SdkError.UserCodeError(IllegalStateException(), "failed", SdkErrorSeverity.WARNING))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `api misuse is ignored`() {
        handler.onError(SdkError.ApiMisuse("Span.setName", "empty name", SdkErrorSeverity.WARNING))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `process handler is registered once and forwards to the attached error handler`() {
        val next = FakeErrorHandler(throwOnSdkError = false)
        val error = SdkError.SdkCodeError(IllegalStateException(), "failed", SdkErrorSeverity.ERROR)
        try {
            ProcessOtelSdkErrorHandler.attach(errorHandler)
            assertFalse(setProcessSdkErrorHandler(SdkErrorHandler {}))

            ProcessOtelSdkErrorHandler.attach(next)
            ProcessOtelSdkErrorHandler.detach(errorHandler)
            ProcessOtelSdkErrorHandler.onError(error)
            assertTrue(errorHandler.sdkErrorMessages.isEmpty())
            assertEquals(1, next.sdkErrorMessages.size)

            ProcessOtelSdkErrorHandler.detach(next)
            ProcessOtelSdkErrorHandler.onError(error)
            assertEquals(1, next.sdkErrorMessages.size)
        } finally {
            ProcessOtelSdkErrorHandler.detach(next)
        }
    }
}
