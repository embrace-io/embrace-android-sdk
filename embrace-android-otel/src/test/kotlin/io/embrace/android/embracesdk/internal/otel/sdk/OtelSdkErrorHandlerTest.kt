package io.embrace.android.embracesdk.internal.otel.sdk

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.opentelemetry.kotlin.error.SdkError
import io.opentelemetry.kotlin.error.SdkErrorSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OtelSdkErrorHandlerTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var handler: OtelSdkErrorHandler

    @Before
    fun setUp() {
        errorHandler = FakeErrorHandler(throwOnInternalError = false)
        handler = OtelSdkErrorHandler(errorHandler)
    }

    @Test
    fun `sdk code error is tracked`() {
        val cause = IllegalStateException("boom")
        handler.onError(SdkError.SdkCodeError(cause, "failed", SdkErrorSeverity.ERROR))

        val msg = errorHandler.internalErrorMessages.single()
        assertEquals(InternalErrorType.OtelSdkCodeError.toString(), msg.msg)
        assertSame(cause, msg.throwable)
    }

    @Test
    fun `user code error is ignored`() {
        handler.onError(SdkError.UserCodeError(IllegalStateException(), "failed", SdkErrorSeverity.WARNING))
        assertTrue(errorHandler.internalErrorMessages.isEmpty())
    }

    @Test
    fun `api misuse is ignored`() {
        handler.onError(SdkError.ApiMisuse("Span.setName", "empty name", SdkErrorSeverity.WARNING))
        assertTrue(errorHandler.internalErrorMessages.isEmpty())
    }
}
