package io.embrace.android.embracesdk.internal.arch.schema

import io.embrace.android.embracesdk.internal.logging.ErrorHandlerImpl
import io.embrace.android.embracesdk.internal.logging.SdkErrorSnapshot
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.opentelemetry.kotlin.semconv.ExceptionAttributes
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SdkErrorSchemaTypeTest {

    @Test
    fun `snapshot sends sdk error details`() {
        val exc = IllegalStateException("Top level", IllegalArgumentException("Root cause"))
        val expected = mapOf(
            ExceptionAttributes.EXCEPTION_TYPE to exc.javaClass.name,
            ExceptionAttributes.EXCEPTION_STACKTRACE to exc.stackTrace.joinToString(
                "\n",
                transform = StackTraceElement::toString,
            ),
            ExceptionAttributes.EXCEPTION_MESSAGE to "Top level",
        )
        assertEquals(expected, SchemaType.SdkError(snapshotOf(exc)).attributes())
    }

    @Test
    fun `null message is sent as an empty string`() {
        val attrs = SchemaType.SdkError(snapshotOf(IllegalStateException())).attributes()
        assertEquals("", attrs[ExceptionAttributes.EXCEPTION_MESSAGE])
    }

    private fun snapshotOf(exc: Throwable): SdkErrorSnapshot {
        val recorded = mutableListOf<SdkErrorSnapshot>()
        val handler = ErrorHandlerImpl({ 0L }, { _, _, _ -> })
        handler.trackSdkError(SdkErrorType.UncaughtExceptionHandler, exc)
        handler.drain { recorded.add(it) }
        return recorded.single()
    }
}
