package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService

internal class EmbraceSessionServiceTest {

    private val initial = fakeSession()
    private lateinit var service: EmbraceSessionService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var spansService: EmbraceSpansService
    private lateinit var configService: FakeConfigService

    companion object {

        private val processStateService = FakeProcessStateService()
        private val clock = FakeClock()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ExecutorService::class)
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    @Before
    fun before() {
        deliveryService = FakeDeliveryService()
        configService = FakeConfigService()
        spansService = EmbraceSpansService(
            clock = OpenTelemetryClock(embraceClock = clock),
            telemetryService = FakeTelemetryService()
        )
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    @Test
    fun `initializing service should detect early sessions`() {
        initializeSessionService(isActivityInBackground = false)
        assertNull(deliveryService.lastSentCachedSession)
    }

    @Test
    fun `on foreground starts state session successfully for cold start`() {
        initializeSessionService()
        val coldStart = true

        service.startSessionWithState(456, coldStart)
        assertNull(deliveryService.lastSentCachedSession)
    }

    @Test
    fun `spanService that is not initialized will not result in any complete spans`() {
        initializeSessionService()
        assertNull(spansService.completedSpans())
    }

    @Test
    fun `session capture disabled after onForeground`() {
        initializeSessionService()

        service.startSessionWithState(clock.now(), true)
        clock.tick(10000)
        // missing end call simulates service being disabled halfway through.

        // nothing is delivered
        assertEquals(0, deliveryService.lastSentSessions.size)

        service.startSessionWithState(clock.now(), false)
        clock.tick(10000L)
        service.endSessionWithState(initial, clock.now())
        assertEquals(1, deliveryService.lastSentSessions.size)
    }

    private fun initializeSessionService(
        isActivityInBackground: Boolean = true
    ) {
        processStateService.isInBackground = isActivityInBackground

        service = EmbraceSessionService(
            deliveryService,
            mockk(relaxed = true),
            FakeClock(),
            PeriodicSessionCacher(FakeClock(), ScheduledWorker(BlockingScheduledExecutorService())),
            Any()
        )
    }
}
