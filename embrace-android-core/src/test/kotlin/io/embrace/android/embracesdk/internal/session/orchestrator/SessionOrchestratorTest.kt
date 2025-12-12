package io.embrace.android.embracesdk.internal.session.orchestrator

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeAppStateTracker
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.FakeLogEnvelopeSource
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePayloadMessageCollator
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeTelemetryDestination
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.behavior.FakeSessionBehavior
import io.embrace.android.embracesdk.fakes.createBackgroundActivityBehavior
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.arch.attrs.embCrashId
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.config.remote.BackgroundActivityRemoteConfig
import io.embrace.android.embracesdk.internal.config.remote.RemoteConfig
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSdkSpan
import io.embrace.android.embracesdk.internal.session.LifeEventType
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.session.id.SessionTrackerImpl
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
internal class SessionOrchestratorTest {

    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var payloadFactory: PayloadFactoryImpl
    private lateinit var payloadCollator: FakePayloadMessageCollator
    private lateinit var logEnvelopeSource: FakeLogEnvelopeSource
    private lateinit var appStateTracker: FakeAppStateTracker
    private lateinit var clock: FakeClock
    private lateinit var configService: FakeConfigService
    private lateinit var userService: FakeUserService
    private lateinit var store: FakePayloadStore
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var sessionTracker: SessionTracker
    private lateinit var payloadCachingService: PayloadCachingService
    private lateinit var sessionCacheExecutor: BlockingScheduledExecutorService
    private lateinit var instrumentationRegistry: InstrumentationRegistry
    private lateinit var fakeDataSource: FakeDataSource
    private lateinit var logger: EmbLogger
    private lateinit var currentSessionSpan: FakeCurrentSessionSpan
    private lateinit var destination: FakeTelemetryDestination
    private var orchestratorStartTimeMs: Long = 0

    @Before
    fun setUp() {
        clock = FakeClock()
        logger = EmbLoggerImpl()
        configService = FakeConfigService(
            backgroundActivityBehavior = createBackgroundActivityBehavior(
                remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 100f))
            )
        )
    }

    @Test
    fun `test initial behavior in background`() {
        createOrchestrator(AppState.BACKGROUND)
        assertEquals(orchestrator, appStateTracker.listeners.single())
        assertEquals(0, payloadCollator.sessionCount.get())
        assertEquals(1, payloadCollator.baCount.get())
        assertEquals(sessionTracker.getActiveSessionId(), currentSessionSpan.getSessionId())
        assertTrue(store.storedSessionPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test initial behavior in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        assertEquals(orchestrator, appStateTracker.listeners.single())
        assertEquals(1, payloadCollator.sessionCount.get())
        assertEquals(0, payloadCollator.baCount.get())
        assertEquals(sessionTracker.getActiveSessionId(), currentSessionSpan.getSessionId())
        assertTrue(store.storedSessionPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test on foreground call after starting in background`() {
        createOrchestrator(AppState.BACKGROUND)
        clock.tick()
        val foregroundTime = clock.now()
        val sessionSpan = currentSessionSpan.sessionSpan
        orchestrator.onForeground()
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = foregroundTime,
            endType = LifeEventType.BKGND_STATE
        )
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `test on background call after starting in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick()
        val backgroundTime = clock.now()
        val sessionSpan = currentSessionSpan.sessionSpan
        orchestrator.onBackground()
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = backgroundTime,
            endType = LifeEventType.STATE
        )
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `saved background activity save overridden after is sent`() {
        createOrchestrator(AppState.BACKGROUND)
        clock.tick()
        orchestrator.onSessionDataUpdate()
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPayloads.size)
        orchestrator.onForeground()
        clock.tick()
        orchestrator.onBackground()
        assertEquals(1, store.cachedSessionPayloads.size)
        assertEquals(2, store.storedSessionPayloads.size)
    }

    @Test
    fun `background activity save invoked after ending will not save it again`() {
        createOrchestrator(AppState.BACKGROUND)
        clock.tick()
        orchestrator.onForeground()
        clock.tick()
        assertEquals(1, store.storedSessionPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.storedSessionPayloads.size)
    }

    @Test
    fun `saved session overridden after it is sent`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick()
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPayloads.size)
        orchestrator.onBackground()
        clock.tick()
        assertEquals(1, store.cachedSessionPayloads.size)
        assertEquals(1, store.storedSessionPayloads.size)
    }

    @Test
    fun `session save invoked after ending will not save it again`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick()
        orchestrator.onBackground()
        clock.tick()
        assertEquals(1, store.storedSessionPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.storedSessionPayloads.size)
    }

    @Test
    fun `end session with manual in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(10000)
        val endTimeMs = clock.now()
        val sessionSpan = currentSessionSpan.sessionSpan
        orchestrator.endSessionWithManual(true)
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = endTimeMs,
            endType = LifeEventType.MANUAL
        )
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `end session with manual in background`() {
        createOrchestrator(AppState.BACKGROUND)
        appStateTracker.state = AppState.BACKGROUND
        orchestrator.endSessionWithManual(true)
        assertTrue(store.storedSessionPayloads.isEmpty())
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `backgrounding with background activity enabled does not cache empty crash envelope`() {
        createOrchestrator(AppState.FOREGROUND)
        orchestrator.onBackground()
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `backgrounding with background activity disabled caches empty crash envelope`() {
        configService = FakeConfigService(
            backgroundActivityBehavior = createBackgroundActivityBehavior(
                remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 0f))
            )
        )
        createOrchestrator(AppState.FOREGROUND)
        orchestrator.onBackground()
        assertEquals(1, store.cachedEmptyCrashPayloads.size)
    }

    @Test
    fun `foregrounding with background activity disabled does not cache empty crash envelope`() {
        configService = FakeConfigService(
            backgroundActivityBehavior = createBackgroundActivityBehavior(
                remoteCfg = RemoteConfig(backgroundActivityConfig = BackgroundActivityRemoteConfig(threshold = 0f))
            )
        )
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `test manual session end disabled for session gating`() {
        configService = FakeConfigService(
            sessionBehavior = FakeSessionBehavior(sessionControlEnabled = true)
        )
        createOrchestrator(AppState.FOREGROUND)

        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual(false)
        assertEquals(1, payloadCollator.sessionCount.get())
        assertTrue(store.storedSessionPayloads.isEmpty())
    }

    @Test
    fun `ending session manually clears user info`() {
        configService = FakeConfigService()
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(10000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, userService.clearedCount)
    }

    @Test
    fun `ending session manually above time threshold succeeds`() {
        configService = FakeConfigService()
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual(true)
        assertEquals(2, payloadCollator.sessionCount.get())
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `ending session manually below time threshold fails`() {
        configService = FakeConfigService()
        createOrchestrator(AppState.FOREGROUND)
        clock.tick(1000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, payloadCollator.sessionCount.get())
        assertTrue(store.storedSessionPayloads.isEmpty())
    }

    @Test
    fun `ending session manually when no session exists does not start a new session`() {
        configService = FakeConfigService()
        createOrchestrator(AppState.BACKGROUND)
        clock.tick(1000)
        orchestrator.endSessionWithManual(true)
        assertEquals(0, payloadCollator.baCount.get())
    }

    @Test
    fun `end with crash in background`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.handleCrash("crashId")
        assertEquals("crashId", destination.attributes[embCrashId.name])
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `end with crash in foreground`() {
        createOrchestrator(AppState.FOREGROUND)
        orchestrator.handleCrash("crashId")
        assertEquals("crashId", destination.attributes[embCrashId.name])
        assertTrue(store.cachedEmptyCrashPayloads.isEmpty())
    }

    @Test
    fun `periodic caching started with initial session`() {
        createOrchestrator(AppState.FOREGROUND)
        assertEquals(0, store.cachedSessionPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPayloads.size)
    }

    @Test
    fun `test session span cold start`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `test session span non cold start`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        orchestrator.onBackground()
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `test session span with crash`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        orchestrator.handleCrash("my-crash-id")
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `test foreground session span heartbeat`() {
        createOrchestrator(AppState.BACKGROUND)
        orchestrator.onForeground()
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes["emb.terminated"])

        // run periodic cache
        clock.tick(2000)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes["emb.terminated"])

        // end with crash
        orchestrator.handleCrash("my-crash-id")
        assertEquals("false", destination.attributes["emb.terminated"])
    }

    @Test
    fun `test background session span heartbeat`() {
        createOrchestrator(AppState.BACKGROUND)
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes["emb.terminated"])

        // run periodic cache
        clock.tick(6000)
        orchestrator.onSessionDataUpdate()
        sessionCacheExecutor.runCurrentlyBlocked()
        assertHeartbeatMatchesClock()
        assertEquals("true", destination.attributes["emb.terminated"])

        // end with crash
        orchestrator.handleCrash("my-crash-id")
        assertEquals("false", destination.attributes["emb.terminated"])
    }

    private fun assertHeartbeatMatchesClock() {
        val attr = checkNotNull(destination.attributes["emb.heartbeat_time_unix_nano"])
        assertEquals(clock.now(), attr.toLong().nanosToMillis())
    }

    private fun createOrchestrator(state: AppState) {
        store = FakePayloadStore()
        appStateTracker = FakeAppStateTracker(state)
        currentSessionSpan = FakeCurrentSessionSpan(clock).apply { initializeService(clock.now()) }
        destination = FakeTelemetryDestination()
        payloadCollator = FakePayloadMessageCollator(currentSessionSpan = currentSessionSpan)
        val payloadSourceModule = FakePayloadSourceModule()
        logEnvelopeSource = payloadSourceModule.logEnvelopeSource
        payloadFactory = PayloadFactoryImpl(
            payloadMessageCollator = payloadCollator,
            logEnvelopeSource = payloadSourceModule.logEnvelopeSource,
            configService = configService,
            logger = logger
        )
        sessionPropertiesService = FakeSessionPropertiesService()
        userService = FakeUserService()
        sessionTracker = SessionTrackerImpl(
            activityManager = null,
            logger = logger
        )
        sessionCacheExecutor = BlockingScheduledExecutorService(clock, true)
        payloadCachingService = PayloadCachingServiceImpl(
            PeriodicSessionCacher(
                BackgroundWorker(sessionCacheExecutor),
                logger
            ),
            clock,
            sessionTracker,
            store
        )
        fakeDataSource = FakeDataSource(RuntimeEnvironment.getApplication())
        instrumentationRegistry = InstrumentationRegistryImpl(
            logger
        ).apply {
            add(
                DataSourceState(
                    factory = { fakeDataSource },
                    configGate = { true }
                )
            )
        }

        orchestrator = SessionOrchestratorImpl(
            appStateTracker,
            payloadFactory,
            clock,
            configService,
            sessionTracker,
            OrchestratorBoundaryDelegate(
                userService,
                sessionPropertiesService
            ),
            store,
            payloadCachingService,
            instrumentationRegistry,
            destination,
            SessionSpanAttrPopulatorImpl(
                destination,
                { 0 },
                FakeLogService(),
                FakeMetadataService()
            )
        )
        orchestratorStartTimeMs = clock.now()
        sessionPropertiesService.addProperty("key", "value", false)
    }

    private fun validateSession(
        sessionSpan: EmbraceSdkSpan?,
        endTimeMs: Long,
        endType: LifeEventType,
    ) {
        assertEquals(endType, endType)
        assertEquals(endTimeMs, checkNotNull(sessionSpan).snapshot()?.endTimeNanos?.nanosToMillis())
    }
}
