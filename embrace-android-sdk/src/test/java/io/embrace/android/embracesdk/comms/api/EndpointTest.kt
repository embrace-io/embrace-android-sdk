package io.embrace.android.embracesdk.comms.api

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EndpointTest {

    private lateinit var scheduledExecutorService: BlockingScheduledExecutorService
    private lateinit var mockExecuteApiCalls: () -> Unit
    private val endpoint = Endpoint.EVENTS

    @Before
    fun setUp() {
        scheduledExecutorService = BlockingScheduledExecutorService()
        mockExecuteApiCalls = mockk()
    }

    @After
    fun tearDown() {
        clearMocks(mockExecuteApiCalls)
        endpoint.clearRateLimit()
    }

    @Test
    fun `test setting a rate limit and scheduling a retry, clears rate limit after succeed`() {
        val retryAfter = 3L
        // clear rate limit after calling executeApiCalls
        every { mockExecuteApiCalls.invoke() } answers { endpoint.clearRateLimit() }
        with(endpoint) {
            updateRateLimitStatus()
            scheduleRetry(
                ScheduledWorker(scheduledExecutorService),
                retryAfter,
                mockExecuteApiCalls
            )
        }

        assertTrue(endpoint.isRateLimited)
        scheduledExecutorService.moveForwardAndRunBlocked(3000)
        assertFalse(endpoint.isRateLimited)
        verify(exactly = 1) { mockExecuteApiCalls.invoke() }
    }

    @Test
    fun `test subsequent rate limit calls without retryAfter are delayed exponentially`() {
        val endpoint = Endpoint.EVENTS
        // emulate 2 rate limit responses and 1 success response
        every { mockExecuteApiCalls.invoke() } answers {
            with(endpoint) {
                updateRateLimitStatus()
                scheduleRetry(
                    ScheduledWorker(scheduledExecutorService),
                    null,
                    mockExecuteApiCalls
                )
            }
        } andThenAnswer {
            with(endpoint) {
                updateRateLimitStatus()
                scheduleRetry(
                    ScheduledWorker(scheduledExecutorService),
                    null,
                    mockExecuteApiCalls
                )
            }
        } andThenAnswer {
            endpoint.clearRateLimit()
        }

        // set rate limit for the first call
        with(endpoint) {
            updateRateLimitStatus()
            scheduleRetry(
                ScheduledWorker(scheduledExecutorService),
                null,
                mockExecuteApiCalls
            )
        }

        // asserts for the first call
        assertTrue(endpoint.isRateLimited)
        verify(exactly = 0) { mockExecuteApiCalls.invoke() }
        scheduledExecutorService.moveForwardAndRunBlocked(3000)
        assertTrue(endpoint.isRateLimited)
        verify(exactly = 1) { mockExecuteApiCalls.invoke() }

        // asserts for the second call
        assertTrue(endpoint.isRateLimited)
        verify(exactly = 1) { mockExecuteApiCalls.invoke() }
        scheduledExecutorService.moveForwardAndRunBlocked(9000)
        assertTrue(endpoint.isRateLimited)
        verify(exactly = 2) { mockExecuteApiCalls.invoke() }

        // asserts for the third call
        assertTrue(endpoint.isRateLimited)
        verify(exactly = 2) { mockExecuteApiCalls.invoke() }
        scheduledExecutorService.moveForwardAndRunBlocked(27000)
        assertFalse(endpoint.isRateLimited)
        verify(exactly = 3) { mockExecuteApiCalls.invoke() }
    }
}
