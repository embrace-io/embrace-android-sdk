package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.fakeSessionMessage
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService

internal class EmbraceSessionServiceTest {

    private lateinit var service: EmbraceSessionService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var spansService: EmbraceSpansService

    companion object {

        private val processStateService = FakeProcessStateService()
        private val ndkService: NdkService = FakeNdkService()
        private val sessionMessage: SessionMessage = fakeSessionMessage()
        private val mockSessionHandler: SessionHandler = mockk(relaxed = true)
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
    fun `initializing service should detect early sessions and start a STATE session`() {
        initializeSessionService(ndkEnabled = true, isActivityInBackground = false)

        assertNotNull(deliveryService.lastSentCachedSession)

        // verify that a STATE session is started
        verify {
            mockSessionHandler.onSessionStarted(
                /* automatically detecting a cold start */ true,
                SessionLifeEventType.STATE,
                any(),
                any()
            )
        }
    }

    @Test
    fun `handle crash successfully`() {
        initializeSessionService()
        val crashId = "crash-id"

        // let's start session first so we have an active session
        every {
            mockSessionHandler.onSessionStarted(
                true,
                SessionLifeEventType.STATE,
                any(),
                any()
            )
        } returns sessionMessage
        service.startSession(true, SessionLifeEventType.STATE, clock.now())

        service.handleCrash(crashId)

        verify { mockSessionHandler.onCrash(crashId) }
    }

    @Test
    fun `on foreground starts state session successfully for cold start`() {
        initializeSessionService()
        val coldStart = true
        val startTime = 123L

        service.onForeground(coldStart, startTime, 456)
        assertEquals("", deliveryService.lastSentCachedSession)

        // verify that a STATE session is started
        verify {
            mockSessionHandler.onSessionStarted(
                coldStart,
                SessionLifeEventType.STATE,
                456,
                any()
            )
        }
    }

    @Test
    fun `on foreground starts state session successfully for non cold start`() {
        initializeSessionService()
        val coldStart = false
        val startTime = 123L

        service.onForeground(coldStart, startTime, 456)

        // verify that a STATE session is started
        verify {
            mockSessionHandler.onSessionStarted(
                coldStart,
                SessionLifeEventType.STATE,
                456,
                any()
            )
        }
    }

    @Test
    fun `trigger stateless end session successfully for activity in background`() {
        initializeSessionService(isActivityInBackground = false)
        // let's start session first so we have an active session
        startDefaultSession()
        clearMocks(mockSessionHandler)
        service.endSessionManually(true)

        // verify session is ended
        verify(exactly = 1) {
            mockSessionHandler.onSessionEnded(
                SessionLifeEventType.MANUAL,
                0,
                true
            )
        }
        verify(exactly = 1) {
            mockSessionHandler.onSessionStarted(
                false,
                SessionLifeEventType.MANUAL,
                0,
                any()
            )
        }
    }

    @Test
    fun `trigger stateless end session successfully for activity in foreground`() {
        initializeSessionService(isActivityInBackground = false)
        // let's start session first so we have an active session
        startDefaultSession()
        val endType = SessionLifeEventType.MANUAL

        service.triggerStatelessSessionEnd(endType)

        // verify session is ended
        verify { mockSessionHandler.onSessionEnded(endType, 0, false) }
        // verify that a MANUAL session is started
        verify {
            mockSessionHandler.onSessionStarted(
                false,
                endType,
                any(),
                any()
            )
        }
    }

    @Test
    fun `trigger stateless end session for a STATE session end type should not do anything`() {
        initializeSessionService()
        service.triggerStatelessSessionEnd(SessionLifeEventType.STATE)
        assertTrue(deliveryService.lastSentSessions.isEmpty())
    }

    @Test
    fun `close successfully`() {
        initializeSessionService()
        service.close()

        verify { mockSessionHandler.close() }
    }

    @Test
    fun `spanService that is not initialized will not result in any complete spans`() {
        initializeSessionService()
        assertNull(spansService.completedSpans())
    }

    private fun initializeSessionService(
        ndkEnabled: Boolean = false,
        isActivityInBackground: Boolean = true
    ) {
        processStateService.isInBackground = isActivityInBackground

        service = EmbraceSessionService(
            processStateService,
            ndkService,
            mockSessionHandler,
            deliveryService,
            ndkEnabled,
            FakeConfigService(),
            clock
        )
    }

    private fun startDefaultSession() {
        every {
            mockSessionHandler.onSessionStarted(
                true,
                SessionLifeEventType.STATE,
                any(),
                any()
            )
        } returns sessionMessage
        service.startSession(true, SessionLifeEventType.STATE, clock.now())
    }
}
