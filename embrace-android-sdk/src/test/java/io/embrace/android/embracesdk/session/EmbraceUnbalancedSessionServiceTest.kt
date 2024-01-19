package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class EmbraceUnbalancedSessionServiceTest {

    private val processStateService = FakeProcessStateService()
    private val ndkService: NdkService = FakeNdkService()
    private val clock = FakeClock()
    private lateinit var service: EmbraceSessionService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var spansService: EmbraceSpansService
    private lateinit var configService: FakeConfigService

    @Before
    fun before() {
        deliveryService = FakeDeliveryService()
        configService = FakeConfigService()
        spansService = EmbraceSpansService(
            clock = OpenTelemetryClock(embraceClock = clock),
            telemetryService = FakeTelemetryService()
        )
    }

    @Test
    fun `simulate session capture enabled after onForeground`() {
        initializeSessionService(sessionHandler = fakeSessionHandler(configService))

        // missing start call simulates service being enabled halfway through.
        service.endSessionWithState(clock.now())

        // nothing is delivered
        assertEquals(0, deliveryService.lastSentSessions.size)

        // next session is recorded correctly
        service.startSessionWithState(false, clock.now())
        clock.tick(10000L)
        service.endSessionWithState(clock.now())
        assertEquals(1, deliveryService.lastSentSessions.size)
    }

    @Test
    fun `session capture disabled after onForeground`() {
        initializeSessionService(sessionHandler = fakeSessionHandler(configService))

        service.startSessionWithState(true, clock.now())
        clock.tick(10000)
        // missing end call simulates service being disabled halfway through.

        // nothing is delivered
        assertEquals(0, deliveryService.lastSentSessions.size)

        service.startSessionWithState(false, clock.now())
        clock.tick(10000L)
        service.endSessionWithState(clock.now())
        assertEquals(1, deliveryService.lastSentSessions.size)
    }

    private fun fakeSessionHandler(configService: ConfigService): SessionHandler {
        return SessionHandler(
            InternalEmbraceLogger(),
            configService,
            FakeUserService(),
            FakeNetworkConnectivityService(),
            FakeAndroidMetadataService(),
            FakeBreadcrumbService(),
            null,
            FakeInternalErrorService(),
            FakeMemoryCleanerService(),
            deliveryService,
            mockk(relaxed = true),
            mockk(relaxed = true),
            FakeClock(),
            SpansService.featureDisabledSpansService,
            ScheduledWorker(BlockingScheduledExecutorService())
        )
    }

    private fun initializeSessionService(
        ndkEnabled: Boolean = false,
        isActivityInBackground: Boolean = true,
        sessionHandler: SessionHandler
    ) {
        processStateService.isInBackground = isActivityInBackground

        service = EmbraceSessionService(
            ndkService,
            sessionHandler,
            deliveryService,
            ndkEnabled,
            clock,
            configService
        )
    }
}
