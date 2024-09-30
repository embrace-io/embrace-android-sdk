package io.embrace.android.embracesdk.internal.session.orchestrator

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionSpan
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeLogService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeMetadataService
import io.embrace.android.embracesdk.fakes.FakePayloadStore
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeSessionPropertiesService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.FakeV2PayloadCollator
import io.embrace.android.embracesdk.fakes.behavior.FakeSessionBehavior
import io.embrace.android.embracesdk.fakes.fakeBackgroundWorker
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.session.SessionPropertiesService
import io.embrace.android.embracesdk.internal.clock.nanosToMillis
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.opentelemetry.embCrashId
import io.embrace.android.embracesdk.internal.payload.LifeEventType
import io.embrace.android.embracesdk.internal.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.message.PayloadFactoryImpl
import io.embrace.android.embracesdk.internal.spans.PersistableEmbraceSpan
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
    private lateinit var payloadCollator: FakeV2PayloadCollator
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var clock: FakeClock
    private lateinit var configService: FakeConfigService
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var userService: FakeUserService
    private lateinit var store: FakePayloadStore
    private lateinit var sessionPropertiesService: SessionPropertiesService
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var periodicSessionCacher: PeriodicSessionCacher
    private lateinit var periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
    private lateinit var sessionCacheExecutor: BlockingScheduledExecutorService
    private lateinit var baCacheExecutor: BlockingScheduledExecutorService
    private lateinit var dataCaptureOrchestrator: DataCaptureOrchestrator
    private lateinit var fakeDataSource: FakeDataSource
    private lateinit var logger: EmbLogger
    private lateinit var currentSessionSpan: FakeCurrentSessionSpan
    private var orchestratorStartTimeMs: Long = 0

    @Before
    fun setUp() {
        clock = FakeClock()
        logger = EmbLoggerImpl()
        configService = FakeConfigService(backgroundActivityCaptureEnabled = true)
    }

    @Test
    fun `test initial behavior in background`() {
        createOrchestrator(true)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(0, payloadCollator.sessionCount.get())
        assertEquals(1, payloadCollator.baCount.get())
        assertEquals(sessionIdTracker.sessionData?.id, currentSessionSpan.getSessionId())
        assertTrue(store.storedSessionPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test initial behavior in foreground`() {
        createOrchestrator(false)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(1, payloadCollator.sessionCount.get())
        assertEquals(0, payloadCollator.baCount.get())
        assertEquals(sessionIdTracker.sessionData?.id, currentSessionSpan.getSessionId())
        assertTrue(store.storedSessionPayloads.isEmpty())
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test on foreground call after starting in background`() {
        createOrchestrator(true)
        clock.tick()
        val foregroundTime = clock.now()
        val sessionSpan = currentSessionSpan.sessionSpan
        orchestrator.onForeground(true, foregroundTime)
        assertEquals(2, memoryCleanerService.callCount)
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = foregroundTime,
            endType = LifeEventType.BKGND_STATE
        )
    }

    @Test
    fun `test on background call after starting in foreground`() {
        createOrchestrator(false)
        clock.tick()
        val backgroundTime = clock.now()
        val sessionSpan = currentSessionSpan.sessionSpan
        orchestrator.onBackground(backgroundTime)
        assertEquals(2, memoryCleanerService.callCount)
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = backgroundTime,
            endType = LifeEventType.STATE
        )
    }

    @Test
    fun `saved background activity save overridden after is sent`() {
        createOrchestrator(true)
        clock.tick()
        baCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPayloads.size)
        orchestrator.onForeground(true, clock.now())
        clock.tick()
        orchestrator.onBackground(clock.now())
        assertEquals(1, store.cachedSessionPayloads.size)
        assertEquals(2, store.storedSessionPayloads.size)
    }

    @Test
    fun `background activity save invoked after ending will not save it again`() {
        createOrchestrator(true)
        clock.tick()
        orchestrator.onForeground(true, clock.now())
        clock.tick()
        assertEquals(1, store.storedSessionPayloads.size)
        baCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.storedSessionPayloads.size)
    }

    @Test
    fun `saved session overridden after it is sent`() {
        createOrchestrator(false)
        clock.tick()
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPayloads.size)
        orchestrator.onBackground(clock.now())
        clock.tick()
        assertEquals(1, store.cachedSessionPayloads.size)
        assertEquals(1, store.storedSessionPayloads.size)
    }

    @Test
    fun `session save invoked after ending will not save it again`() {
        createOrchestrator(false)
        clock.tick()
        orchestrator.onBackground(clock.now())
        clock.tick()
        assertEquals(1, store.storedSessionPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.storedSessionPayloads.size)
    }

    @Test
    fun `end session with manual in foreground`() {
        createOrchestrator(false)
        clock.tick(10000)
        val endTimeMs = clock.now()
        val sessionSpan = currentSessionSpan.sessionSpan
        orchestrator.endSessionWithManual(true)
        assertEquals(2, memoryCleanerService.callCount)
        validateSession(
            sessionSpan = sessionSpan,
            endTimeMs = endTimeMs,
            endType = LifeEventType.MANUAL
        )
    }

    @Test
    fun `end session with manual in background`() {
        createOrchestrator(true)
        processStateService.isInBackground = true
        orchestrator.endSessionWithManual(true)
        assertEquals(1, memoryCleanerService.callCount)
        assertTrue(store.storedSessionPayloads.isEmpty())
    }

    @Test
    fun `test manual session end disabled for session gating`() {
        configService = FakeConfigService(
            sessionBehavior = FakeSessionBehavior(sessionControlEnabled = true)
        )
        createOrchestrator(false)

        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual(false)
        assertEquals(1, payloadCollator.sessionCount.get())
        assertEquals(1, memoryCleanerService.callCount)
        assertTrue(store.storedSessionPayloads.isEmpty())
    }

    @Test
    fun `ending session manually clears user info`() {
        configService = FakeConfigService()
        createOrchestrator(false)
        clock.tick(10000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, userService.clearedCount)
    }

    @Test
    fun `ending session manually above time threshold succeeds`() {
        configService = FakeConfigService()
        createOrchestrator(false)
        clock.tick(10000)
        assertEquals(1, payloadCollator.sessionCount.get())
        orchestrator.endSessionWithManual(true)
        assertEquals(2, payloadCollator.sessionCount.get())
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `ending session manually below time threshold fails`() {
        configService = FakeConfigService()
        createOrchestrator(false)
        clock.tick(1000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, payloadCollator.sessionCount.get())
        assertTrue(store.storedSessionPayloads.isEmpty())
    }

    @Test
    fun `ending session manually when no session exists doesn not start a new session`() {
        configService = FakeConfigService()
        createOrchestrator(true)
        clock.tick(1000)
        orchestrator.endSessionWithManual(true)
        assertEquals(0, payloadCollator.baCount.get())
    }

    @Test
    fun `end with crash in background`() {
        configService = FakeConfigService(
            backgroundActivityCaptureEnabled = true,
        )
        createOrchestrator(true)
        orchestrator.handleCrash("crashId")
        assertEquals("crashId", currentSessionSpan.getAttribute(embCrashId.name))
    }

    @Test
    fun `end with crash in foreground`() {
        configService = FakeConfigService(
            backgroundActivityCaptureEnabled = true,
        )
        createOrchestrator(false)
        orchestrator.handleCrash("crashId")
        assertEquals("crashId", currentSessionSpan.getAttribute(embCrashId.name))
    }

    @Test
    fun `periodic caching started with initial session`() {
        createOrchestrator(false)
        assertEquals(0, store.cachedSessionPayloads.size)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, store.cachedSessionPayloads.size)
    }

    @Test
    fun `test session span cold start`() {
        createOrchestrator(true)
        orchestrator.onForeground(true, clock.now())
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `test session span non cold start`() {
        createOrchestrator(true)
        orchestrator.onForeground(true, orchestratorStartTimeMs)
        orchestrator.onBackground(orchestratorStartTimeMs)
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `test session span with crash`() {
        createOrchestrator(true)
        orchestrator.onForeground(true, orchestratorStartTimeMs)
        orchestrator.handleCrash("my-crash-id")
        checkNotNull(store.storedSessionPayloads.last().first)
    }

    @Test
    fun `test foreground session span heartbeat`() {
        createOrchestrator(true)
        orchestrator.onForeground(true, orchestratorStartTimeMs)
        assertHeartbeatMatchesClock()
        assertEquals("true", currentSessionSpan.getAttribute("emb.terminated"))

        // run periodic cache
        clock.tick(2000)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertHeartbeatMatchesClock()
        assertEquals("true", currentSessionSpan.getAttribute("emb.terminated"))

        // end with crash
        orchestrator.handleCrash("my-crash-id")
        assertEquals("false", currentSessionSpan.getAttribute("emb.terminated"))
    }

    @Test
    fun `test background session span heartbeat`() {
        createOrchestrator(true)
        assertHeartbeatMatchesClock()
        assertEquals("true", currentSessionSpan.getAttribute("emb.terminated"))

        // run periodic cache
        clock.tick(6000)
        baCacheExecutor.runCurrentlyBlocked()
        assertHeartbeatMatchesClock()
        assertEquals("true", currentSessionSpan.getAttribute("emb.terminated"))

        // end with crash
        orchestrator.handleCrash("my-crash-id")
        assertEquals("false", currentSessionSpan.getAttribute("emb.terminated"))
    }

    private fun assertHeartbeatMatchesClock() {
        val attr = checkNotNull(currentSessionSpan.getAttribute("emb.heartbeat_time_unix_nano"))
        assertEquals(clock.now(), attr.toLong().nanosToMillis())
    }

    private fun createOrchestrator(background: Boolean) {
        store = FakePayloadStore()
        processStateService = FakeProcessStateService(background)
        currentSessionSpan = FakeCurrentSessionSpan(clock).apply { initializeService(clock.now()) }
        payloadCollator = FakeV2PayloadCollator(currentSessionSpan = currentSessionSpan)
        payloadFactory = PayloadFactoryImpl(
            payloadMessageCollator = payloadCollator,
            configService = configService,
            logger = logger
        )
        memoryCleanerService = FakeMemoryCleanerService()
        sessionPropertiesService = FakeSessionPropertiesService()
        userService = FakeUserService()
        sessionIdTracker = FakeSessionIdTracker()
        sessionCacheExecutor = BlockingScheduledExecutorService(clock, true)
        baCacheExecutor = BlockingScheduledExecutorService(clock, true)
        periodicSessionCacher = PeriodicSessionCacher(
            BackgroundWorker(sessionCacheExecutor),
            logger
        )
        periodicBackgroundActivityCacher =
            PeriodicBackgroundActivityCacher(
                clock,
                BackgroundWorker(baCacheExecutor),
                logger
            )
        fakeDataSource = FakeDataSource(RuntimeEnvironment.getApplication())
        dataCaptureOrchestrator = DataCaptureOrchestrator(
            configService,
            fakeBackgroundWorker(),
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
            processStateService,
            payloadFactory,
            clock,
            configService,
            sessionIdTracker,
            OrchestratorBoundaryDelegate(
                memoryCleanerService,
                userService,
                sessionPropertiesService
            ),
            store,
            periodicSessionCacher,
            periodicBackgroundActivityCacher,
            dataCaptureOrchestrator,
            currentSessionSpan,
            SessionSpanAttrPopulatorImpl(
                currentSessionSpan,
                FakeEventService(),
                { 0L },
                FakeLogService(),
                FakeMetadataService()
            ),
            logger
        )
        orchestratorStartTimeMs = clock.now()
        sessionPropertiesService.addProperty("key", "value", false)
    }

    private fun validateSession(
        sessionSpan: PersistableEmbraceSpan?,
        endTimeMs: Long,
        endType: LifeEventType
    ) {
        assertEquals(endType, endType)
        assertEquals(endTimeMs, checkNotNull(sessionSpan).snapshot()?.endTimeNanos?.nanosToMillis())
    }
}
