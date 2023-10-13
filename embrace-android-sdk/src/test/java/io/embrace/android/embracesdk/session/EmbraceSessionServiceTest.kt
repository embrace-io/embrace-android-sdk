package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.config.remote.SpansRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.fakeSpansBehavior
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.Session.SessionLifeEventType
import io.embrace.android.embracesdk.payload.SessionMessage
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.ExecutorService

internal class EmbraceSessionServiceTest {

    private lateinit var service: EmbraceSessionService
    private lateinit var configService: FakeConfigService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var spansService: EmbraceSpansService

    companion object {

        private val activityService = FakeActivityService()
        private val mockNdkService: NdkService = mockk(relaxUnitFun = true)
        private val mockSession: Session = mockk(relaxed = true)
        private val mockSessionMessage: SessionMessage = mockk(relaxed = true)
        private val mockSessionHandler: SessionHandler = mockk(relaxed = true)
        private val mockSessionProperties: EmbraceSessionProperties = mockk(relaxed = true)
        private val clock = FakeClock()

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ExecutorService::class)
            every { mockSessionMessage.session } returns mockSession
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
        spansService = EmbraceSpansService(clock = OpenTelemetryClock(embraceClock = clock))
        configService = FakeConfigService(
            spansBehavior = fakeSpansBehavior { SpansRemoteConfig(pctEnabled = 100f) }
        )
        configService.addListener(spansService)
        configService.updateListeners()
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
                Session.SessionLifeEventType.STATE,
                any(),
                mockSessionProperties,
                any(),
                any()
            )
        }
    }

    @Test
    fun `start session successfully`() {
        initializeSessionService()
        val coldStart = /* same for false */ true
        val type = /* could be any type */ Session.SessionLifeEventType.STATE
        every {
            mockSessionHandler.onSessionStarted(
                coldStart,
                type,
                any(),
                mockSessionProperties,
                any(),
                any()
            )
        } returns mockSessionMessage

        val startTime = clock.now()

        service.startSession(coldStart, type, startTime)

        assertEquals(mockSession, service.getActiveSession())
        verify {
            mockSessionHandler.onSessionStarted(
                coldStart,
                type,
                startTime,
                mockSessionProperties,
                any(),
                any()
            )
        }
        assertEquals(mockSession, service.getActiveSession())
    }

    @Test
    fun `start session if not allowed then session handler will return a null session`() {
        initializeSessionService()
        val coldStart = /* same for false */ true
        val type = /* could be any type */ Session.SessionLifeEventType.STATE
        every {
            mockSessionHandler.onSessionStarted(
                coldStart,
                type,
                any(),
                mockSessionProperties,
                any(),
                any()
            )
        } returns null

        val startTime = clock.now()
        service.startSession(coldStart, type, startTime)

        assertNull(service.getActiveSession())
        verify {
            mockSessionHandler.onSessionStarted(
                coldStart,
                type,
                startTime,
                mockSessionProperties,
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
                Session.SessionLifeEventType.STATE,
                any(),
                mockSessionProperties,
                any(),
                any()
            )
        } returns mockSessionMessage
        service.startSession(true, Session.SessionLifeEventType.STATE, clock.now())

        service.handleCrash(crashId)

        verify { mockSessionHandler.onCrash(mockSession, crashId, mockSessionProperties, 0) }
    }

    @Test
    fun `on foreground starts state session successfully for cold start`() {
        initializeSessionService()
        val coldStart = true
        val startTime = 123L

        service.onForeground(coldStart, startTime, 456)
        assertNull(deliveryService.lastSentCachedSession)

        // verify that a STATE session is started
        verify {
            mockSessionHandler.onSessionStarted(
                coldStart,
                Session.SessionLifeEventType.STATE,
                456,
                mockSessionProperties,
                any(),
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
                Session.SessionLifeEventType.STATE,
                456,
                mockSessionProperties,
                any(),
                any()
            )
        }
    }

    @Test
    fun `on background ends a state session for a previously existing session, with an sdkStarupDuration = 5`() {
        initializeSessionService()
        val sdkStartupDuration = 5L
        service.setSdkStartupDuration(sdkStartupDuration)
        // let's start session first so we have an active session
        startDefaultSession()

        service.onBackground(456)

        // verify session is ended
        verify {
            mockSessionHandler.onSessionEnded(
                Session.SessionLifeEventType.STATE,
                mockSession,
                mockSessionProperties,
                sdkStartupDuration,
                456
            )
        }
        // verify active session has been reset
        assertNull(service.getActiveSession())
    }

    @Test
    fun `trigger stateless end session successfully for activity in background`() {
        initializeSessionService()
        // let's start session first so we have an active session
        startDefaultSession()

        service.triggerStatelessSessionEnd(Session.SessionLifeEventType.MANUAL)

        // verify session is ended
        verify {
            mockSessionHandler.onSessionEnded(
                Session.SessionLifeEventType.MANUAL,
                mockSession,
                mockSessionProperties,
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
        val endType = Session.SessionLifeEventType.MANUAL

        service.triggerStatelessSessionEnd(endType)

        // verify session is ended
        verify { mockSessionHandler.onSessionEnded(endType, mockSession, mockSessionProperties, 0, any()) }
        // verify that a MANUAL session is started
        verify {
            mockSessionHandler.onSessionStarted(
                false,
                endType,
                any(),
                mockSessionProperties,
                any(),
                any()
            )
        }
    }

    @Test
    fun `trigger stateless end session for a STATE session end type should not do anything`() {
        initializeSessionService()
        service.triggerStatelessSessionEnd(Session.SessionLifeEventType.STATE)

        verify { mockSessionHandler wasNot Called }
    }

    @Test
    fun `close successfully`() {
        initializeSessionService()
        service.close()

        verify { mockSessionHandler.close() }
    }

    @Test
    fun `add property successfully`() {
        initializeSessionService()
        val key = "key"
        val value = "value"
        val permanent = true
        val properties = mapOf<String, String>()
        every { mockSessionProperties.add(key, value, permanent) } returns true
        every { mockSessionProperties.get() } returns properties

        val added = service.addProperty(key, value, permanent)

        assertTrue(added)
        verify { mockSessionProperties.add(key, value, permanent) }
        verify { mockNdkService.onSessionPropertiesUpdate(properties) }
    }

    @Test
    fun `if add property failed, then it should not notify ndk service`() {
        initializeSessionService()
        val key = "key"
        val value = "value"
        val permanent = true
        every { mockSessionProperties.add(key, value, permanent) } returns false

        val added = service.addProperty(key, value, permanent)

        assertFalse(added)
        verify { mockSessionProperties.add(key, value, permanent) }
        verify { mockNdkService wasNot Called }
    }

    @Test
    fun `remove property successfully`() {
        initializeSessionService()
        val key = "key"
        val properties = mapOf<String, String>()
        every { mockSessionProperties.remove(key) } returns true
        every { mockSessionProperties.get() } returns properties

        val removed = service.removeProperty(key)

        assertTrue(removed)
        verify { mockSessionProperties.remove(key) }
        verify { mockNdkService.onSessionPropertiesUpdate(properties) }
    }

    @Test
    fun `if remove property failed, then it should not notify ndk service`() {
        initializeSessionService()
        val key = "key"
        every { mockSessionProperties.remove(key) } returns false

        val removed = service.removeProperty(key)

        assertFalse(removed)
        verify { mockSessionProperties.remove(key) }
        verify { mockNdkService wasNot Called }
    }

    @Test
    fun `get embrace session properties`() {
        val properties = mapOf<String, String>()
        every { mockSessionProperties.get() } returns properties

        initializeSessionService()
        assertEquals(properties, service.getProperties())
    }

    @Test
    fun `verify periodic caching`() {
        initializeSessionService()

        service.onPeriodicCacheActiveSession()

        verify {
            mockSessionHandler.getActiveSessionEndMessage(
                /* either null active session or valid active session, same test */ null,
                mockSessionProperties,
                0
            )
        }

        assertNotNull(deliveryService.lastSavedSession)
    }

    @Test
    fun `spanService that is not initialized will not result in any complete spans`() {
        initializeSessionService()
        assertNull(spansService.completedSpans())
    }

    @Test
    fun `backgrounding flushes completed spans`() {
        initializeSessionService()
        startDefaultSession()
        val now = clock.now()
        spansService.initializeService(now, now + 5L)
        assertEquals(1, spansService.completedSpans()?.size)
        service.onBackground(now)
        // expect 2 spans to be flushed: session span and sdk init span
        verify {
            mockSessionHandler.onSessionEnded(
                endType = any(),
                originSession = any(),
                sessionProperties = any(),
                sdkStartupDuration = any(),
                endTime = any(),
                completedSpans = match {
                    it.size == 2
                }
            )
        }
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `stateless session ends flushes completed spans`() {
        listOf(SessionLifeEventType.MANUAL, SessionLifeEventType.TIMED).forEach {
            before()
            initializeSessionService()
            startDefaultSession()
            val now = clock.now()
            spansService.initializeService(now, now + 5L)
            assertEquals(1, spansService.completedSpans()?.size)
            service.triggerStatelessSessionEnd(it)
            // expect 2 spans to be flushed: session span and sdk init span
            verify {
                mockSessionHandler.onSessionEnded(
                    endType = any(),
                    originSession = any(),
                    sessionProperties = any(),
                    sdkStartupDuration = any(),
                    endTime = any(),
                    completedSpans = match {
                        it.size == 2
                    }
                )
            }
            assertEquals(0, spansService.completedSpans()?.size)
            after()
        }
    }

    @Test
    fun `crash ending flushes completed spans`() {
        initializeSessionService()
        startDefaultSession()
        val now = clock.now()
        spansService.initializeService(now, now + 5L)
        assertEquals(1, spansService.completedSpans()?.size)
        service.handleCrash("crashId")
        // expect 2 spans to be flushed: session span and sdk init span
        verify {
            mockSessionHandler.onCrash(
                originSession = any(),
                crashId = any(),
                sessionProperties = any(),
                sdkStartupDuration = any(),
                completedSpans = match {
                    it.size == 2
                }
            )
        }
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `periodic caching caches completed spans but doesn't flush them`() {
        initializeSessionService()
        startDefaultSession()
        val now = clock.now()
        spansService.initializeService(now, now + 5L)
        assertEquals(1, spansService.completedSpans()?.size)
        service.onPeriodicCacheActiveSession()
        assertEquals(1, spansService.completedSpans()?.size)
    }

    private fun initializeSessionService(
        ndkEnabled: Boolean = false,
        isActivityInBackground: Boolean = true
    ) {
        activityService.isInBackground = isActivityInBackground

        service = EmbraceSessionService(
            activityService,
            mockNdkService,
            mockSessionProperties,
            mockk(relaxed = true),
            mockSessionHandler,
            deliveryService,
            ndkEnabled,
            clock,
            spansService
        )
    }

    private fun startDefaultSession() {
        every {
            mockSessionHandler.onSessionStarted(
                true,
                Session.SessionLifeEventType.STATE,
                any(),
                mockSessionProperties,
                any(),
                any()
            )
        } returns mockSessionMessage
        service.startSession(true, Session.SessionLifeEventType.STATE, clock.now())
    }
}
