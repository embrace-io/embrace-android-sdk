package io.embrace.android.embracesdk.session

import android.app.Activity
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.thermalstate.NoOpThermalStatusService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.event.EmbraceRemoteLogger
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.fakes.FakeActivityTracker
import io.embrace.android.embracesdk.fakes.FakeAndroidMetadataService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fixtures.testSpan
import io.embrace.android.embracesdk.internal.MessageType
import io.embrace.android.embracesdk.internal.OpenTelemetryClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger
import io.embrace.android.embracesdk.ndk.NdkService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.prefs.PreferencesService
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
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
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class SessionHandlerTest {

    private lateinit var sessionHandler: SessionHandler

    companion object {
        private val logger: InternalEmbraceLogger = InternalEmbraceLogger()
        private val preferencesService: PreferencesService = mockk(relaxed = true)
        private val userService: FakeUserService = FakeUserService()
        private val mockNetworkConnectivityService: NetworkConnectivityService =
            mockk(relaxUnitFun = true)
        private val mockBreadcrumbService: BreadcrumbService = mockk(relaxed = true)
        private val activityLifecycleTracker = FakeActivityTracker()
        private val mockNdkService: NdkService = mockk(relaxUnitFun = true)
        private val mockEventService: EventService = mockk(relaxed = true)
        private val mockRemoteLogger: EmbraceRemoteLogger = mockk(relaxed = true)
        private val mockExceptionService: EmbraceInternalErrorService = mockk(relaxed = true)
        private val mockPerformanceInfoService: PerformanceInfoService = mockk(relaxed = true)
        private val mockMemoryCleanerService: MemoryCleanerService = mockk(relaxUnitFun = true)
        private val mockWebViewservice: WebViewService = mockk(relaxed = true) {
            every { getCapturedData() } returns emptyList()
        }
        private val clock = FakeClock()
        private val mockAutomaticSessionStopper: ScheduledExecutorService = mockk(relaxed = true)
        private val mockSessionPeriodicCacheExecutorService: ScheduledExecutorService =
            mockk(relaxed = true)
        private const val sessionUuid = "99fcae22-0db5-4b63-b49d-315eecce4889"
        private const val now = 123L
        private var sessionNumber = 5
        private val mockSessionProperties: EmbraceSessionProperties = mockk(relaxed = true)
        private val emptyMapSessionProperties: Map<String, String> = emptyMap()
        private val mockUserInfo: UserInfo = mockk()
        private val mockAutomaticSessionStopperRunnable: Runnable = mockk()
        private var mockActiveSession: Session = mockk(relaxed = true)

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            mockkStatic(ScheduledExecutorService::class)
            mockkStatic(ExecutorService::class)
            mockkStatic(Uuid::class)

            clock.setCurrentTime(now)
            every { Uuid.getEmbUuid() } returns sessionUuid
        }

        @AfterClass
        @JvmStatic
        fun afterClass() {
            unmockkAll()
        }
    }

    private lateinit var metadataService: FakeAndroidMetadataService
    private lateinit var localConfig: LocalConfig
    private lateinit var remoteConfig: RemoteConfig
    private lateinit var sessionLocalConfig: SessionLocalConfig
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var gatingService: FakeGatingService
    private lateinit var configService: FakeConfigService
    private lateinit var spansService: EmbraceSpansService

    @Before
    fun before() {
        mockActiveSession = mockk(relaxed = true)
        every { mockSessionProperties.get() } returns emptyMapSessionProperties

        metadataService = FakeAndroidMetadataService()
        localConfig = LocalConfig(
            appId = metadataService.getAppId(),
            ndkEnabled = true,
            sdkConfig = SdkLocalConfig()
        )
        sessionLocalConfig = SessionLocalConfig()
        remoteConfig = RemoteConfig()
        configService = FakeConfigService(
            autoDataCaptureBehavior = fakeAutoDataCaptureBehavior(
                localCfg = { localConfig },
                remoteCfg = { remoteConfig }
            ),
            sessionBehavior = fakeSessionBehavior(
                localCfg = { sessionLocalConfig },
                remoteCfg = { remoteConfig }
            ),
            dataCaptureEventBehavior = fakeDataCaptureEventBehavior(
                remoteCfg = { remoteConfig }
            )
        )
        gatingService = FakeGatingService(configService = configService)
        deliveryService = FakeDeliveryService()
        val sessionMessageCollator = SessionMessageCollator(
            configService,
            metadataService,
            mockEventService,
            mockRemoteLogger,
            mockExceptionService,
            mockPerformanceInfoService,
            mockWebViewservice,
            NoOpThermalStatusService(),
            null,
            mockBreadcrumbService,
            userService,
            clock
        )
        spansService = EmbraceSpansService(OpenTelemetryClock(embraceClock = clock), FakeTelemetryService())
        sessionHandler = SessionHandler(
            logger,
            configService,
            preferencesService,
            userService,
            mockNetworkConnectivityService,
            metadataService,
            mockBreadcrumbService,
            activityLifecycleTracker,
            mockNdkService,
            mockExceptionService,
            mockMemoryCleanerService,
            deliveryService,
            sessionMessageCollator,
            mockSessionProperties,
            clock,
            spansService,
            automaticSessionStopper = mockAutomaticSessionStopper,
            sessionPeriodicCacheExecutorService = mockSessionPeriodicCacheExecutorService
        )
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    @Test
    fun `onSession started successfully`() {
        val maxSessionSeconds = 60
        sessionLocalConfig = SessionLocalConfig(maxSessionSeconds = 60, asyncEnd = false)
        userService.obj = mockUserInfo
        mockActiveSession = fakeSession()

        val screen = "screen"
        every { mockBreadcrumbService.getLastViewBreadcrumbScreenName() } returns screen
        val sessionStartType = Session.SessionLifeEventType.STATE
        // this is needed so session handler creates automatic session stopper

        val sessionMessage = sessionHandler.onSessionStarted(
            true,
            sessionStartType,
            now,
            mockAutomaticSessionStopperRunnable
        )

        // verify record connection type
        verify { mockNetworkConnectivityService.networkStatusOnSessionStarted(now) }
        // verify active session is set
        assertEquals(sessionUuid, metadataService.activeSessionId)
        // verify automatic session stopper has been scheduled
        verify {
            mockAutomaticSessionStopper.schedule(
                mockAutomaticSessionStopperRunnable,
                maxSessionSeconds.toLong(),
                TimeUnit.SECONDS
            )
        }
        // verify periodic caching worker has been scheduled
        verify {
            mockSessionPeriodicCacheExecutorService.scheduleWithFixedDelay(
                any(),
                0,
                2,
                TimeUnit.SECONDS
            )
        }
        // verify session id gets updated if ndk enabled
        verify { mockNdkService.updateSessionId(sessionUuid) }
        // verify session is correctly built
        with(checkNotNull(sessionMessage?.session)) {
            assertEquals(sessionUuid, this.sessionId)
            assertEquals(startTime, now)
            assertTrue(isColdStart)
            assertEquals(sessionStartType, startType)
            assertEquals(emptyMapSessionProperties, properties)
            assertEquals("st", messageType)
            assertEquals("foreground", appState)
            assertEquals(mockUserInfo, user)
        }
        // verify session message is successfully built
        with(checkNotNull(sessionMessage)) {
            assertEquals(metadataService.getDeviceInfo(), deviceInfo)
            assertEquals(metadataService.getAppInfo(), appInfo)
        }
        verify(exactly = 1) { preferencesService.incrementAndGetSessionNumber() }
    }

    @Test
    fun `onSession if it's not allowed to start should not do anything`() {
        remoteConfig = RemoteConfig(
            disabledMessageTypes = setOf(MessageType.SESSION.name.toLowerCase(Locale.getDefault()))
        )

        val sessionMessage = sessionHandler.onSessionStarted(
            true,
            /* any event type */ Session.SessionLifeEventType.STATE,
            now,
            mockAutomaticSessionStopperRunnable
        )

        assertNull(sessionMessage)
        verify { mockNetworkConnectivityService wasNot Called }
        assertNull(metadataService.activeSessionId)
        assertEquals(0, gatingService.sessionMessagesFiltered.size)
        verify { mockAutomaticSessionStopper wasNot Called }
        verify { mockSessionPeriodicCacheExecutorService wasNot Called }
        verify { mockNdkService wasNot Called }
    }

    @Test
    fun `onSession started successfully with no preference service session number`() {
        // return absent session number
        sessionNumber = 0
        sessionLocalConfig = SessionLocalConfig(maxSessionSeconds = 5, asyncEnd = false)
        every { mockBreadcrumbService.getLastViewBreadcrumbScreenName() } returns "screen"
        val sessionStartType = Session.SessionLifeEventType.STATE
        // this is needed so session handler creates automatic session stopper

        val sessionMessage = sessionHandler.onSessionStarted(
            true,
            sessionStartType,
            now,
            mockAutomaticSessionStopperRunnable
        )

        verify(exactly = 1) { preferencesService.incrementAndGetSessionNumber() }
        checkNotNull(sessionMessage)
        assertNotNull(sessionMessage.session)
        // no need to verify anything else because it's already verified in another test case
    }

    @Test
    fun `onSession started with no maximum session seconds should not start session automatic stopper`() {
        every { mockBreadcrumbService.getLastViewBreadcrumbScreenName() } returns "screen"
        val sessionStartType = Session.SessionLifeEventType.STATE
        sessionLocalConfig = SessionLocalConfig(maxSessionSeconds = null)

        val sessionMessage = sessionHandler.onSessionStarted(
            true,
            sessionStartType,
            now,
            mockAutomaticSessionStopperRunnable
        )

        // verify automatic session stopper has not been scheduled
        verify { mockAutomaticSessionStopper wasNot Called }
        checkNotNull(sessionMessage)
        assertNotNull(sessionMessage.session)
        // no need to verify anything else because it's already verified in another test case
    }

    @Test
    fun `onSession started and resuming with no previous screen name but with foregroundActivity, it should force log view breadcrumb`() {
        every { mockBreadcrumbService.getLastViewBreadcrumbScreenName() } returns null
        val mockActivity: Activity = mockk()
        // let's return a foreground activity
        activityLifecycleTracker.foregroundActivity = mockActivity
        val activityClassName = "activity-class-name"
        every { mockActivity.localClassName } returns activityClassName
        val sessionStartType = Session.SessionLifeEventType.STATE

        val sessionMessage = sessionHandler.onSessionStarted(
            true,
            sessionStartType,
            now,
            mockAutomaticSessionStopperRunnable
        )

        // verify we are forcing log view with foreground activity class name
        verify(exactly = 1) { mockBreadcrumbService.forceLogView(activityClassName, now) }
        checkNotNull(sessionMessage)
        assertNotNull(sessionMessage.session)
        // no need to verify anything else because it's already verified in another test case
    }

    @Test
    fun `onSession not allowed to end because session control is disabled for MANUAL event type`() {
        sessionHandler.onSessionEnded(
            Session.SessionLifeEventType.MANUAL,
            1000,
            false
        )

        verify { mockSessionPeriodicCacheExecutorService wasNot Called }
        verify { mockAutomaticSessionStopper wasNot Called }
        verify { mockMemoryCleanerService wasNot Called }
        verify { mockSessionProperties wasNot Called }
        assertTrue(deliveryService.lastSentSessions.isEmpty())
        assertEquals(0, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `onSession not allowed to end because session control is disabled for TIMED event type`() {
        sessionHandler.onSessionEnded(
            Session.SessionLifeEventType.TIMED,
            1000,
            false
        )

        verify { mockSessionPeriodicCacheExecutorService wasNot Called }
        verify { mockAutomaticSessionStopper wasNot Called }
        verify { mockMemoryCleanerService wasNot Called }
        verify { mockSessionProperties wasNot Called }
        assertTrue(deliveryService.lastSentSessions.isEmpty())
        assertEquals(0, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `onSession not allowed to end MANUALLY because session duration is less than 5 seconds`() {
        remoteConfig = RemoteConfig(sessionConfig = SessionRemoteConfig(isEnabled = true))
        // since now=123, then duration will be less than 5 seconds
        val startTime = 120L
        every { mockActiveSession.startTime } returns startTime

        sessionHandler.onSessionEnded(
            Session.SessionLifeEventType.MANUAL,
            1000,
            false
        )

        verify { mockSessionPeriodicCacheExecutorService wasNot Called }
        verify { mockAutomaticSessionStopper wasNot Called }
        verify { mockMemoryCleanerService wasNot Called }
        verify { mockSessionProperties wasNot Called }
        assertTrue(deliveryService.lastSentSessions.isEmpty())
        assertEquals(0, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `if session messages are disabled don't end session but end periodic cache and session automatic stopper`() {
        startFakeSession()
        remoteConfig = RemoteConfig(
            disabledMessageTypes = setOf(MessageType.SESSION.name.toLowerCase(Locale.getDefault()))
        )
        sessionHandler.scheduledFuture = mockk(relaxed = true)

        sessionHandler.onSessionEnded(
            /* any type */ Session.SessionLifeEventType.STATE,
            1000,
            false
        )

        // verify automatic session stopper was called
        verify { sessionHandler.scheduledFuture?.cancel(false) }
        verify { mockMemoryCleanerService wasNot Called }
        assertEquals(0, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `onCrash ended session successfully`() {
        startFakeSession()

        val crashId = "crash-id"
        val startTime = 120L
        val sdkStartupDuration = 2L
        mockActiveSession = fakeSession().copy(
            startTime = startTime,
            isColdStart = true
        )

        sessionHandler.onCrash(crashId)

        // when crashing, the following calls should not be made, this is because since we're
        // about to crash we can save some time on not doing these //
        verify { mockMemoryCleanerService wasNot Called }
        verify(exactly = 0) { mockSessionProperties.clearTemporary() }

        val session = checkNotNull(deliveryService.lastSavedSession).session

        with(session) {
            assertFalse(checkNotNull(isEndedCleanly))
            assertEquals("en", messageType)
            assertEquals("foreground", appState)
            assertEquals(emptyList<String>(), eventIds)
            assertEquals(emptyList<String>(), infoLogIds)
            assertEquals(emptyList<String>(), warningLogIds)
            assertEquals(emptyList<String>(), errorLogIds)
            assertEquals(emptyList<String>(), networkLogIds)
            assertEquals(0, infoLogsAttemptedToSend)
            assertEquals(0, warnLogsAttemptedToSend)
            assertEquals(0, errorLogsAttemptedToSend)
            assertTrue(checkNotNull(exceptionError).exceptionErrors.isEmpty())
            assertEquals(now, lastHeartbeatTime)
            assertEquals(mockSessionProperties.get(), properties)
            assertEquals(Session.SessionLifeEventType.STATE, endType)
            assertEquals(0, unhandledExceptions)
            assertEquals(crashId, crashReportId)
            assertEquals(now, endTime)
            assertEquals(sdkStartupDuration, sdkStartupDuration)
            assertEquals(0L, startupDuration)
            assertEquals(0L, startupThreshold)
            assertEquals(0, webViewInfo?.size)
        }
    }

    @Test
    fun `onPeriodicCacheActiveSession caches session successfully`() {
        startFakeSession()
        val sessionMessage = sessionHandler.onPeriodicCacheActiveSessionImpl()

        assertNotNull(sessionMessage)

        // when periodic caching, the following calls should not be made
        verify { mockMemoryCleanerService wasNot Called }
        verify(exactly = 0) { mockSessionProperties.clearTemporary() }
        assertEquals(0, gatingService.sessionMessagesFiltered.size)
    }

    @Test
    fun `verify close stops everything successfully`() {
        startFakeSession()
        sessionHandler.scheduledFuture = mockk(relaxed = true)
        sessionHandler.close()
        verify { sessionHandler.scheduledFuture?.cancel(false) }
    }

    @Test
    fun `endSession includes completed spans in message`() {
        startFakeSession()
        spansService.initializeService(now, now + 5L)
        spansService.recordSpan("test-span") {
            // do nothing
        }
        clock.tick(30000)
        sessionHandler.onSessionEnded(
            endType = Session.SessionLifeEventType.STATE,
            endTime = 10L,
            false
        )
        assertSpanInSessionMessage(deliveryService.lastSentSessions.last().first)
    }

    @Test
    fun `clearing user info disallowed for state sessions`() {
        startFakeSession()
        clock.tick(30000)
        sessionHandler.onSessionEnded(
            endType = Session.SessionLifeEventType.STATE,
            endTime = 10L,
            true
        )
        assertEquals(0, userService.clearedCount)
    }

    @Test
    fun `endSession clears user info`() {
        remoteConfig = remoteConfig.copy(
            sessionConfig = SessionRemoteConfig(
                isEnabled = true
            )
        )
        sessionHandler.onSessionStarted(
            coldStart = true,
            startType = Session.SessionLifeEventType.MANUAL,
            startTime = clock.now(),
            automaticSessionCloserCallback = mockAutomaticSessionStopperRunnable
        )
        clock.tick(30000)
        sessionHandler.onSessionEnded(
            endType = Session.SessionLifeEventType.MANUAL,
            endTime = clock.now(),
            true
        )
        assertEquals(1, userService.clearedCount)
    }

    @Test
    fun `crashes includes completed spans in message`() {
        startFakeSession()
        spansService.initializeService(now, now + 5L)
        spansService.recordSpan("test-span") {
            // do nothing
        }
        sessionHandler.onCrash("fakeCrashId")
        assertSpanInSessionMessage(deliveryService.lastSavedSession)
    }

    @Test
    fun `periodically cached sessions included currently completed spans`() {
        startFakeSession()
        spansService.initializeService(now, now + 5L)
        val sessionMessage = sessionHandler.onPeriodicCacheActiveSessionImpl(listOf(testSpan))
        val spans = checkNotNull(sessionMessage?.spans)
        assertEquals(testSpan, spans.single())
    }

    @Test
    fun `start session successfully`() {
        assertNull(sessionHandler.getSessionId())
        startFakeSession()
        assertNotNull(sessionHandler.getSessionId())
    }

    @Test
    fun `verify periodic caching`() {
        startFakeSession()
        sessionHandler.onPeriodicCacheActiveSessionImpl()
        val session = checkNotNull(deliveryService.lastSavedSession).session
        assertEquals(false, session.isEndedCleanly)
        assertEquals(true, session.isReceivedTermination)
    }

    @Test
    fun `backgrounding flushes completed spans`() {
        startFakeSession()

        spansService.initializeService(now, now + 5L)
        assertEquals(1, spansService.completedSpans()?.size)

        clock.tick(15000L)
        sessionHandler.onSessionEnded(
            endType = Session.SessionLifeEventType.STATE,
            endTime = clock.now(),
            false
        )

        val sessionMessage = checkNotNull(deliveryService.lastSentSessions.last().first)
        val spans = checkNotNull(sessionMessage.spans)
        assertEquals(2, spans.size)
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `crash ending flushes completed spans`() {
        startFakeSession()
        spansService.initializeService(now, now + 5L)
        assertEquals(1, spansService.completedSpans()?.size)

        sessionHandler.onCrash("crashId")
        assertEquals(0, spansService.completedSpans()?.size)
    }

    @Test
    fun `session message is sent`() {
        startFakeSession()
        clock.tick(10000)
        sessionHandler.onSessionEnded(
            endType = Session.SessionLifeEventType.STATE,
            endTime = clock.now(),
            false
        )
        val sessions = deliveryService.lastSentSessions
        assertEquals(1, sessions.size)
        assertEquals(1, sessions.count { it.second == SessionSnapshotType.NORMAL_END })
    }

    private fun startFakeSession() {
        sessionHandler.onSessionStarted(
            coldStart = true,
            startType = Session.SessionLifeEventType.STATE,
            startTime = clock.now(),
            automaticSessionCloserCallback = mockAutomaticSessionStopperRunnable
        )
    }

    private fun assertSpanInSessionMessage(sessionMessage: SessionMessage?) {
        assertNotNull(sessionMessage)
        val spans = checkNotNull(sessionMessage?.spans)
        assertEquals(3, spans.size)
        val expectedSpans = listOf("emb-sdk-init", "emb-test-span", "emb-session-span")
        assertEquals(expectedSpans, spans.map(EmbraceSpanData::name))
    }
}
