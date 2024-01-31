package io.embrace.android.embracesdk.session

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.FakeSessionPropertiesService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.thermalstate.NoOpThermalStatusService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.local.LocalConfig
import io.embrace.android.embracesdk.config.local.SdkLocalConfig
import io.embrace.android.embracesdk.config.local.SessionLocalConfig
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.event.LogMessageService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeGatingService
import io.embrace.android.embracesdk.fakes.FakeLogMessageService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePerformanceInfoService
import io.embrace.android.embracesdk.fakes.FakePreferenceService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeStartupService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeWebViewService
import io.embrace.android.embracesdk.fakes.fakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.fakeDataCaptureEventBehavior
import io.embrace.android.embracesdk.fakes.fakeSession
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.spans.EmbraceSpanData
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.internal.spans.SpansSink
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService
import io.embrace.android.embracesdk.payload.Session
import io.embrace.android.embracesdk.payload.SessionMessage
import io.embrace.android.embracesdk.session.message.PayloadFactory
import io.embrace.android.embracesdk.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.session.message.PayloadMessageCollator
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.ScheduledWorker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class SessionHandlerTest {

    companion object {
        private val eventService: EventService = FakeEventService()
        private val logMessageService: LogMessageService = FakeLogMessageService()
        private val clock = FakeClock()
        private val internalErrorService =
            EmbraceInternalErrorService(FakeProcessStateService(), clock, false)
        private const val now = 123L
        private var sessionNumber = 5
        private val sessionProperties: EmbraceSessionProperties = mockk(relaxed = true)
        private val emptyMapSessionProperties: Map<String, String> = emptyMap()
    }

    private val initial = fakeSession(startMs = now)
    private val userService: FakeUserService = FakeUserService()
    private val performanceInfoService: PerformanceInfoService = FakePerformanceInfoService()
    private val webViewService: WebViewService = FakeWebViewService()
    private var activeSession: Session = fakeSession()

    private lateinit var spansSink: SpansSink
    private lateinit var spansService: SpansService
    private lateinit var preferencesService: FakePreferenceService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var metadataService: FakeMetadataService
    private lateinit var localConfig: LocalConfig
    private lateinit var remoteConfig: RemoteConfig
    private lateinit var sessionLocalConfig: SessionLocalConfig
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var gatingService: FakeGatingService
    private lateinit var configService: FakeConfigService
    private lateinit var ndkService: FakeNdkService
    private lateinit var breadcrumbService: FakeBreadcrumbService
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var payloadFactory: PayloadFactory
    private lateinit var executorService: BlockingScheduledExecutorService
    private lateinit var scheduledWorker: ScheduledWorker

    @Before
    fun before() {
        executorService = BlockingScheduledExecutorService()
        scheduledWorker = ScheduledWorker(executorService)
        clock.setCurrentTime(now)
        activeSession = fakeSession()
        every { sessionProperties.get() } returns emptyMapSessionProperties
        ndkService = FakeNdkService()
        metadataService = FakeMetadataService()
        sessionIdTracker = FakeSessionIdTracker()
        breadcrumbService = FakeBreadcrumbService()
        breadcrumbService.viewBreadcrumbScreenName = "screen"
        memoryCleanerService = FakeMemoryCleanerService()

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
        preferencesService = FakePreferenceService()
        deliveryService = FakeDeliveryService()
        val initModule = FakeInitModule(clock = clock)
        spansSink = initModule.spansSink
        spansService = initModule.spansService
        val payloadMessageCollator = PayloadMessageCollator(
            configService,
            metadataService,
            eventService,
            logMessageService,
            internalErrorService,
            performanceInfoService,
            webViewService,
            NoOpThermalStatusService(),
            null,
            breadcrumbService,
            userService,
            preferencesService,
            initModule.spansSink,
            initModule.currentSessionSpan,
            clock,
            FakeSessionPropertiesService(),
            FakeStartupService()
        )
        payloadFactory = PayloadFactoryImpl(payloadMessageCollator)
    }

    @After
    fun after() {
        clearAllMocks(answers = false)
    }

    @Test
    fun `onSession started successfully with no preference service session number`() {
        // return absent session number
        sessionNumber = 0
        sessionLocalConfig = SessionLocalConfig()
        // this is needed so session handler creates automatic session stopper

        payloadFactory.startSessionWithState(now, true)

        assertEquals(1, preferencesService.incrementAndGetSessionNumberCount)
    }

    @Test
    fun `onCrash ended session successfully`() {
        startFakeSession()

        val crashId = "crash-id"
        val startTime = 120L
        val sdkStartupDuration = 2L
        activeSession = fakeSession().copy(
            startTime = startTime,
            isColdStart = true
        )

        val session = payloadFactory.endSessionWithCrash(initial, clock.now(), crashId).session

        // when crashing, the following calls should not be made, this is because since we're
        // about to crash we can save some time on not doing these //
        assertEquals(0, memoryCleanerService.callCount)
        verify(exactly = 0) { sessionProperties.clearTemporary() }

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
            assertNull(exceptionError)
            assertEquals(now, lastHeartbeatTime)
            assertEquals(sessionProperties.get(), properties)
            assertEquals(Session.LifeEventType.STATE, endType)
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
    fun `endSession includes completed spans in message`() {
        startFakeSession()
        initializeServices()
        spansService.recordSpan("test-span") {
            // do nothing
        }
        clock.tick(30000)
        val msg = payloadFactory.endSessionWithState(initial, 10L)
        assertSpanInSessionMessage(msg)
    }

    @Test
    fun `clearing user info disallowed for state sessions`() {
        startFakeSession()
        clock.tick(30000)
        payloadFactory.endSessionWithState(initial, 10L)
        assertEquals(0, userService.clearedCount)
    }

    @Test
    fun `crashes includes completed spans in message`() {
        startFakeSession()
        initializeServices()
        spansService.recordSpan("test-span") {
            // do nothing
        }
        val msg = payloadFactory.endSessionWithCrash(initial, clock.now(), "fakeCrashId")
        assertSpanInSessionMessage(msg)
    }

    @Test
    fun `start session successfully`() {
        assertNotNull(startFakeSession())
    }

    @Test
    fun `backgrounding flushes completed spans`() {
        startFakeSession()
        initializeServices()
        spansService.recordSpan("test-span") {}
        assertEquals(1, spansSink.completedSpans().size)

        clock.tick(15000L)
        val sessionMessage = payloadFactory.endSessionWithState(initial, clock.now())
        val spans = checkNotNull(sessionMessage.spans)
        assertEquals(2, spans.size)
        assertEquals(0, spansSink.completedSpans().size)
    }

    @Test
    fun `crash ending flushes completed spans`() {
        startFakeSession()
        initializeServices()
        spansService.recordSpan("test-span") {}
        assertEquals(1, spansSink.completedSpans().size)

        payloadFactory.endSessionWithCrash(initial, clock.now(), "crashId")
        assertEquals(0, spansSink.completedSpans().size)
    }

    private fun startFakeSession(): Session {
        return payloadFactory.startSessionWithState(now, true)
    }

    private fun initializeServices(startTimeMillis: Long = clock.now()) {
        spansService.initializeService(startTimeMillis)
    }

    private fun assertSpanInSessionMessage(sessionMessage: SessionMessage?) {
        assertNotNull(sessionMessage)
        val spans = checkNotNull(sessionMessage?.spans)
        assertEquals(2, spans.size)
        val expectedSpans = listOf("emb-test-span", "emb-session-span")
        assertEquals(expectedSpans, spans.map(EmbraceSpanData::name))
    }
}
