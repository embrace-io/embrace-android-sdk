package io.embrace.android.embracesdk.benchmark.scenario

import io.embrace.android.embracesdk.benchmark.scenario.RecordingScope.Companion.START_TIME_MS
import io.embrace.android.embracesdk.network.http.HttpMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

internal class ScenarioScopeTest {

    private val scope = RecordingScope()

    @Test
    fun `a recorded request is a completed request that took 80ms of scenario time`() {
        scope.recordRequest("https://api.example.com/v1/cart", HttpMethod.POST, statusCode = 503, bytesReceived = 12)

        assertEquals(80, scope.elapsedMs)
        with(scope.embrace.networkRequests.single()) {
            assertEquals("https://api.example.com/v1/cart", url)
            assertEquals("POST", httpMethod)
            assertEquals(START_TIME_MS, startTime)
            assertEquals(START_TIME_MS + 80, endTime)
            assertEquals(512L, bytesSent)
            assertEquals(12L, bytesReceived)
            assertEquals(503, responseCode)
        }
    }

    @Test
    fun `a request given only its url and times is a successful GET with a 4 KB body`() {
        scope.recordRequest("https://api.example.com/v1/cart", START_TIME_MS - 250, START_TIME_MS - 30)

        with(scope.embrace.networkRequests.single()) {
            assertEquals("GET", httpMethod)
            assertEquals(200, responseCode)
            assertEquals(4_096L, bytesReceived)
        }
    }

    @Test
    fun `a request recorded at given times keeps them and leaves the scenario clock where it was`() {
        scope.recordRequest(
            "https://api.example.com/v1/cart",
            startTimeMs = START_TIME_MS - 250,
            endTimeMs = START_TIME_MS - 30,
            method = HttpMethod.POST,
            statusCode = 503,
            bytesReceived = 12,
        )

        assertEquals(0, scope.elapsedMs)
        with(scope.embrace.networkRequests.single()) {
            assertEquals("https://api.example.com/v1/cart", url)
            assertEquals("POST", httpMethod)
            assertEquals(START_TIME_MS - 250, startTime)
            assertEquals(START_TIME_MS - 30, endTime)
            assertEquals(512L, bytesSent)
            assertEquals(12L, bytesReceived)
            assertEquals(503, responseCode)
        }
    }

    @Test
    fun `a request may take no time but cannot end before it starts`() {
        scope.recordRequest("https://api.example.com/v1/cart", START_TIME_MS, START_TIME_MS)

        assertThrows(IllegalArgumentException::class.java) {
            scope.recordRequest("https://api.example.com/v1/cart", START_TIME_MS, START_TIME_MS - 1)
        }
        assertEquals(1, scope.embrace.networkRequests.size)
    }
}
