package io.embrace.android.embracesdk.internal.comms.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ApiResponseTest {

    @Test
    fun `test retry logic`() {
        assertFalse(ApiResponse.Success(null, null).shouldRetry)
        assertFalse(ApiResponse.NotModified.shouldRetry)
        assertFalse(ApiResponse.PayloadTooLarge.shouldRetry)
        assertFalse(ApiResponse.Failure(400, null).shouldRetry)

        assertTrue(ApiResponse.TooManyRequests(Endpoint.LOGS, null).shouldRetry)
        assertTrue(ApiResponse.Incomplete(RuntimeException()).shouldRetry)
        assertTrue(ApiResponse.None.shouldRetry)
        assertTrue(ApiResponse.Failure(500, null).shouldRetry)
    }
}
