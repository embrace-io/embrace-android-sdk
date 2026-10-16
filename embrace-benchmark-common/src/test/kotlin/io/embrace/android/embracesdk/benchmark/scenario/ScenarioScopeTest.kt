package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.benchmark.scenario.RecordingScope.Companion.START_TIME_MS
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.network.http.HttpMethod
import io.mockk.every
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ScenarioScopeTest {

    private val sdk = standInSdk()
    private val scope = RecordingScope(sdk)

    @Test
    fun `a recorded request is a completed request that took 80ms of scenario time`() {
        val request = slot<EmbraceNetworkRequest>()
        every { sdk.recordNetworkRequest(capture(request)) } returns Unit

        scope.recordRequest("https://api.example.com/v1/cart", HttpMethod.POST, statusCode = 503, bytesReceived = 12)

        assertEquals(80, scope.elapsedMs)
        with(request.captured) {
            assertEquals("https://api.example.com/v1/cart", url)
            assertEquals("POST", httpMethod)
            assertEquals(START_TIME_MS, startTime)
            assertEquals(START_TIME_MS + 80, endTime)
            assertEquals(512L, bytesSent)
            assertEquals(12L, bytesReceived)
            assertEquals(503, responseCode)
        }
    }
}
