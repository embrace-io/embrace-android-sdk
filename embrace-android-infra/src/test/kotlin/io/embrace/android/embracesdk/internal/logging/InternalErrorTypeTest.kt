package io.embrace.android.embracesdk.internal.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class InternalErrorTypeTest {

    @Test
    fun `ERROR severity is captured`() {
        assertTrue(InternalErrorType.UncaughtExceptionHandler.shouldCapture())
    }

    @Test
    fun `toString returns simple class name`() {
        assertEquals("UncaughtExceptionHandler", InternalErrorType.UncaughtExceptionHandler.toString())
        assertEquals("DeliverySchedulingFail", InternalErrorType.DeliverySchedulingFail.toString())
    }
}
