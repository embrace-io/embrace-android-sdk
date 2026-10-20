package io.embrace.android.embracesdk.internal.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class SdkErrorTypeTest {

    @Test
    fun `ERROR severity is captured`() {
        assertTrue(SdkErrorType.UncaughtExceptionHandler.shouldCapture())
    }

    @Test
    fun `toString returns simple class name`() {
        assertEquals("UncaughtExceptionHandler", SdkErrorType.UncaughtExceptionHandler.toString())
        assertEquals("DeliverySchedulingFail", SdkErrorType.DeliverySchedulingFail.toString())
    }
}
