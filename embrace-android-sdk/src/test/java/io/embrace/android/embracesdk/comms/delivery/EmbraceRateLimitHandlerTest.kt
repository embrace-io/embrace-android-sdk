package io.embrace.android.embracesdk.comms.delivery

import io.embrace.android.embracesdk.comms.api.EmbraceApiService.Companion.Endpoint
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceRateLimitHandlerTest {

    private lateinit var scheduledExecutorService: BlockingScheduledExecutorService
    private lateinit var rateLimitHandler: RateLimitHandler
    private lateinit var mockExecuteApiCalls: () -> Unit
    private val endpoint = Endpoint.EVENTS

    @Before
    fun setUp() {
        scheduledExecutorService = BlockingScheduledExecutorService()
        rateLimitHandler = EmbraceRateLimitHandler(scheduledExecutorService)
        mockExecuteApiCalls = mockk()
    }

    @After
    fun tearDown() {
        clearMocks(mockExecuteApiCalls)
        rateLimitHandler.clearRateLimit(endpoint)
    }

    @Test
    fun `test setRateLimitAndRetry retries api calls and clears rate limit after succeed`() {
        val retryAfter = 3L
        // clear rate limit after calling executeApiCalls
        every { mockExecuteApiCalls.invoke() } answers { rateLimitHandler.clearRateLimit(endpoint) }
        rateLimitHandler.setRateLimitAndScheduleRetry(endpoint, retryAfter, mockExecuteApiCalls)

        assertTrue(rateLimitHandler.isRateLimited(endpoint))
        scheduledExecutorService.moveForwardAndRunBlocked(3000)
        assertFalse(rateLimitHandler.isRateLimited(endpoint))
        verify(exactly = 1) { mockExecuteApiCalls.invoke() }
    }

    @Test
    fun `test subsequent setRateLimitAndRetry calls without retryAfter are delayed exponentially`() {
        val endpoint = Endpoint.EVENTS
        // emulate 2 rate limit responses and 1 success response
        every { mockExecuteApiCalls.invoke() } answers {
            rateLimitHandler.setRateLimitAndScheduleRetry(endpoint, null, mockExecuteApiCalls)
        } andThenAnswer {
            rateLimitHandler.setRateLimitAndScheduleRetry(endpoint, null, mockExecuteApiCalls)
        } andThenAnswer {
            rateLimitHandler.clearRateLimit(endpoint)
        }

        // set rate limit for the first call
        rateLimitHandler.setRateLimitAndScheduleRetry(endpoint, null, mockExecuteApiCalls)

        // asserts for the first call
        assertTrue(rateLimitHandler.isRateLimited(endpoint))
        verify(exactly = 0) { mockExecuteApiCalls.invoke() }
        scheduledExecutorService.moveForwardAndRunBlocked(3000)
        assertTrue(rateLimitHandler.isRateLimited(endpoint))
        verify(exactly = 1) { mockExecuteApiCalls.invoke() }

        // asserts for the second call
        assertTrue(rateLimitHandler.isRateLimited(endpoint))
        verify(exactly = 1) { mockExecuteApiCalls.invoke() }
        scheduledExecutorService.moveForwardAndRunBlocked(9000)
        assertTrue(rateLimitHandler.isRateLimited(endpoint))
        verify(exactly = 2) { mockExecuteApiCalls.invoke() }

        // asserts for the third call
        assertTrue(rateLimitHandler.isRateLimited(endpoint))
        verify(exactly = 2) { mockExecuteApiCalls.invoke() }
        scheduledExecutorService.moveForwardAndRunBlocked(27000)
        assertFalse(rateLimitHandler.isRateLimited(endpoint))
        verify(exactly = 3) { mockExecuteApiCalls.invoke() }
    }
}
