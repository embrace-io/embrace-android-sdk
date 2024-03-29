package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.FakeBreadcrumbService
import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.FakePayloadFactory
import io.embrace.android.embracesdk.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDataSource
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeSessionIdTracker
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.fakes.system.mockContext
import io.embrace.android.embracesdk.session.caching.PeriodicBackgroundActivityCacher
import io.embrace.android.embracesdk.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.ScheduledWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SessionOrchestratorTest {

    companion object {
        private const val TIMESTAMP = 1000L
    }

    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var payloadFactory: FakePayloadFactory
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var clock: FakeClock
    private lateinit var configService: FakeConfigService
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var internalErrorService: FakeInternalErrorService
    private lateinit var userService: FakeUserService
    private lateinit var ndkService: FakeNdkService
    private lateinit var deliveryService: FakeDeliveryService
    private lateinit var sessionProperties: EmbraceSessionProperties
    private lateinit var sessionIdTracker: FakeSessionIdTracker
    private lateinit var periodicSessionCacher: PeriodicSessionCacher
    private lateinit var periodicBackgroundActivityCacher: PeriodicBackgroundActivityCacher
    private lateinit var sessionCacheExecutor: BlockingScheduledExecutorService
    private lateinit var baCacheExecutor: BlockingScheduledExecutorService
    private lateinit var dataCaptureOrchestrator: DataCaptureOrchestrator
    private lateinit var fakeDataSource: FakeDataSource

    @Before
    fun setUp() {
        deliveryService = FakeDeliveryService()
        processStateService = FakeProcessStateService()
        payloadFactory = FakePayloadFactory()
        clock = FakeClock()
        configService = FakeConfigService(backgroundActivityCaptureEnabled = true)
        memoryCleanerService = FakeMemoryCleanerService()
        internalErrorService = FakeInternalErrorService()
        sessionProperties = fakeEmbraceSessionProperties()
        userService = FakeUserService()
        ndkService = FakeNdkService()
        sessionIdTracker = FakeSessionIdTracker()
        sessionCacheExecutor = BlockingScheduledExecutorService(clock, true)
        baCacheExecutor = BlockingScheduledExecutorService(clock, true)
        periodicSessionCacher = PeriodicSessionCacher(ScheduledWorker(sessionCacheExecutor))
        periodicBackgroundActivityCacher =
            PeriodicBackgroundActivityCacher(clock, ScheduledWorker(baCacheExecutor))
        fakeDataSource = FakeDataSource(mockContext())
        dataCaptureOrchestrator = DataCaptureOrchestrator(
            listOf(
                DataSourceState(
                    factory = { fakeDataSource },
                    configGate = { true },
                    currentSessionType = null
                )
            )
        )

        orchestrator = SessionOrchestratorImpl(
            processStateService,
            payloadFactory,
            clock,
            configService,
            sessionIdTracker,
            OrchestratorBoundaryDelegate(
                memoryCleanerService,
                userService,
                ndkService,
                sessionProperties,
                internalErrorService,
                FakeNetworkConnectivityService(),
                FakeBreadcrumbService()
            ),
            deliveryService,
            periodicSessionCacher,
            periodicBackgroundActivityCacher,
            dataCaptureOrchestrator
        )
        sessionProperties.add("key", "value", false)
    }

    @Test
    fun `test initial behavior in background`() {
        createOrchestrator(true)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(0, payloadFactory.startSessionTimestamps.size)
        assertEquals(1, payloadFactory.startBaTimestamps.size)
        assertEquals("fake-activity", sessionIdTracker.sessionId)
        assertEquals(0, deliveryService.lastSentSessions.size)
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test initial behavior in foreground`() {
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(1, payloadFactory.startSessionTimestamps.size)
        assertEquals(0, payloadFactory.startBaTimestamps.size)
        assertEquals("fakeSessionId", sessionIdTracker.sessionId)
        assertEquals(0, deliveryService.lastSentSessions.size)
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test on foreground call`() {
        createOrchestrator(true)
        orchestrator.onForeground(true, TIMESTAMP)
        assertEquals(2, memoryCleanerService.callCount)
        assertEquals(TIMESTAMP, payloadFactory.startSessionTimestamps.single())
        assertEquals(TIMESTAMP, payloadFactory.endBaTimestamps.single())
        assertEquals("fakeSessionId", sessionIdTracker.sessionId)
        assertEquals(1, deliveryService.lastSentSessions.size)
        assertEquals(1, fakeDataSource.enableDataCaptureCount)
    }

    @Test
    fun `test on background call`() {
        orchestrator.onBackground(TIMESTAMP)
        assertEquals(2, memoryCleanerService.callCount)
        assertEquals(TIMESTAMP, payloadFactory.endSessionTimestamps.single())
        assertEquals(TIMESTAMP, payloadFactory.startBaTimestamps.single())
        assertEquals("fake-activity", sessionIdTracker.sessionId)
        assertEquals(1, deliveryService.lastSentSessions.size)
    }

    @Test
    fun `end session with manual in foreground`() {
        clock.tick(10000)
        orchestrator.endSessionWithManual(true)
        assertEquals(2, memoryCleanerService.callCount)
        assertEquals(1, payloadFactory.manualSessionEndCount)
        assertEquals(1, payloadFactory.manualSessionStartCount)
        assertEquals("fakeSessionId", sessionIdTracker.sessionId)
        assertEquals(1, deliveryService.lastSentSessions.size)
    }

    @Test
    fun `end session with manual in background`() {
        processStateService.isInBackground = true
        orchestrator.endSessionWithManual(true)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, payloadFactory.manualSessionEndCount)
        assertEquals(0, payloadFactory.manualSessionStartCount)
        assertEquals(0, deliveryService.lastSentSessions.size)
    }

    @Test
    fun `test manual session end disabled for session gating`() {
        configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior {
                RemoteConfig(
                    sessionConfig = SessionRemoteConfig(
                        isEnabled = true
                    ),
                )
            }
        )
        createOrchestrator(false)

        clock.tick(10000)
        orchestrator.endSessionWithManual(false)
        assertEquals(1, payloadFactory.startSessionTimestamps.size)
        assertEquals(0, payloadFactory.manualSessionEndCount)
        assertEquals(1, memoryCleanerService.callCount)
        assertEquals(0, deliveryService.lastSentSessions.size)
    }

    @Test
    fun `ending session manually clears user info`() {
        configService = FakeConfigService()
        createOrchestrator(false)
        clock.tick(10000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, userService.clearedCount)
        assertEquals(1, ndkService.userUpdateCount)
    }

    @Test
    fun `ending session manually above time threshold succeeds`() {
        configService = FakeConfigService()
        createOrchestrator(false)
        clock.tick(10000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, payloadFactory.startSessionTimestamps.size)
        assertEquals(1, payloadFactory.manualSessionStartCount)
        assertEquals(1, payloadFactory.manualSessionEndCount)
    }

    @Test
    fun `ending session manually below time threshold fails`() {
        configService = FakeConfigService()
        createOrchestrator(false)
        clock.tick(1000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, payloadFactory.startSessionTimestamps.size)
        assertEquals(0, payloadFactory.manualSessionStartCount)
        assertEquals(0, payloadFactory.manualSessionEndCount)
    }

    @Test
    fun `ending session manually when no session exists starts new session`() {
        configService = FakeConfigService()
        createOrchestrator(true)
        clock.tick(1000)

        orchestrator.endSessionWithManual(true)
        assertEquals(0, payloadFactory.startSessionTimestamps.size)
        assertEquals(0, payloadFactory.manualSessionStartCount)
        assertEquals(0, payloadFactory.manualSessionEndCount)
    }

    @Test
    fun `end with crash in background`() {
        configService = FakeConfigService(backgroundActivityCaptureEnabled = true)
        createOrchestrator(true)
        orchestrator.endSessionWithCrash("crashId")
        assertEquals("crashId", payloadFactory.baCrashId)
        assertEquals(1, deliveryService.lastSentSessions.size)
    }

    @Test
    fun `end with crash in foreground`() {
        configService = FakeConfigService(backgroundActivityCaptureEnabled = true)
        createOrchestrator(false)
        orchestrator.endSessionWithCrash("crashId")
        assertEquals("crashId", payloadFactory.crashId)
        assertEquals(1, deliveryService.lastSentSessions.size)
    }

    @Test
    fun `periodic caching started with initial session`() {
        assertEquals(0, payloadFactory.snapshotSessionCount)
        sessionCacheExecutor.runCurrentlyBlocked()
        assertEquals(1, payloadFactory.snapshotSessionCount)
    }

    private fun createOrchestrator(background: Boolean) {
        processStateService.listeners.clear()
        processStateService.isInBackground = background
        payloadFactory.startSessionTimestamps.clear()
        payloadFactory.startBaTimestamps.clear()
        memoryCleanerService = FakeMemoryCleanerService()
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            payloadFactory,
            clock,
            configService,
            sessionIdTracker,
            OrchestratorBoundaryDelegate(
                memoryCleanerService,
                userService,
                ndkService,
                sessionProperties,
                internalErrorService,
                FakeNetworkConnectivityService(),
                FakeBreadcrumbService()
            ),
            deliveryService,
            periodicSessionCacher,
            periodicBackgroundActivityCacher,
            dataCaptureOrchestrator
        )
    }
}
