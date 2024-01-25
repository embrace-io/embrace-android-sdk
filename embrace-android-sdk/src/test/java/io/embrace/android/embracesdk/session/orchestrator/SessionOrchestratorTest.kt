package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.FakeNdkService
import io.embrace.android.embracesdk.FakeSessionService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.config.remote.SessionRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeBackgroundActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.FakeUserService
import io.embrace.android.embracesdk.fakes.fakeEmbraceSessionProperties
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SessionOrchestratorTest {

    companion object {
        private const val TIMESTAMP = 1000L
    }

    private lateinit var orchestrator: SessionOrchestratorImpl
    private lateinit var sessionService: FakeSessionService
    private lateinit var backgroundActivityService: FakeBackgroundActivityService
    private lateinit var processStateService: FakeProcessStateService
    private lateinit var clock: FakeClock
    private lateinit var configService: FakeConfigService
    private lateinit var memoryCleanerService: FakeMemoryCleanerService
    private lateinit var internalErrorService: FakeInternalErrorService
    private lateinit var userService: FakeUserService
    private lateinit var ndkService: FakeNdkService
    private lateinit var sessionProperties: EmbraceSessionProperties

    @Before
    fun setUp() {
        processStateService = FakeProcessStateService()
        sessionService = FakeSessionService()
        backgroundActivityService = FakeBackgroundActivityService()
        clock = FakeClock()
        configService = FakeConfigService(backgroundActivityCaptureEnabled = true)
        memoryCleanerService = FakeMemoryCleanerService()
        internalErrorService = FakeInternalErrorService()
        sessionProperties = fakeEmbraceSessionProperties()
        userService = FakeUserService()
        ndkService = FakeNdkService()
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            sessionService,
            backgroundActivityService,
            clock,
            configService,
            memoryCleanerService,
            userService,
            ndkService,
            sessionProperties,
            internalErrorService
        )
        sessionProperties.add("key", "value", false)
    }

    @Test
    fun `test initial behavior in background`() {
        createOrchestrator(true)
        verifyPrepareEnvelopeCalled(0)
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(0, sessionService.startTimestamps.size)
        assertEquals(1, backgroundActivityService.startTimestamps.size)
    }

    @Test
    fun `test initial behavior in foreground`() {
        verifyPrepareEnvelopeCalled(0)
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(1, sessionService.startTimestamps.size)
        assertEquals(0, backgroundActivityService.startTimestamps.size)
    }

    @Test
    fun `test on foreground call`() {
        createOrchestrator(true)
        orchestrator.onForeground(true, TIMESTAMP)
        verifyPrepareEnvelopeCalled()
        assertEquals(TIMESTAMP, sessionService.startTimestamps.single())
        assertEquals(TIMESTAMP, backgroundActivityService.endTimestamps.single())
    }

    @Test
    fun `test on background call`() {
        orchestrator.onBackground(TIMESTAMP)
        verifyPrepareEnvelopeCalled()
        assertEquals(TIMESTAMP, sessionService.endTimestamps.single())
        assertEquals(TIMESTAMP, backgroundActivityService.startTimestamps.single())
    }

    @Test
    fun `end session with manual in foreground`() {
        clock.tick(10000)
        orchestrator.endSessionWithManual(true)
        verifyPrepareEnvelopeCalled()
        assertEquals(1, sessionService.manualEndCount)
        assertEquals(1, sessionService.manualStartCount)
    }

    @Test
    fun `end session with manual in background`() {
        processStateService.isInBackground = true
        orchestrator.endSessionWithManual(true)
        verifyPrepareEnvelopeCalled(0)
        assertEquals(0, sessionService.manualEndCount)
        assertEquals(0, sessionService.manualStartCount)
    }

    @Test
    fun `test background activity capture disabled`() {
        configService = FakeConfigService(backgroundActivityCaptureEnabled = false)
        createOrchestrator(true)
        orchestrator.onBackground(TIMESTAMP)
        verifyPrepareEnvelopeCalled()
        assertTrue(backgroundActivityService.startTimestamps.isEmpty())
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
        assertEquals(1, sessionService.startTimestamps.size)
        assertEquals(0, sessionService.manualEndCount)
        verifyPrepareEnvelopeCalled(0)
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
        assertEquals(1, sessionService.startTimestamps.size)
        assertEquals(1, sessionService.manualStartCount)
        assertEquals(1, sessionService.manualEndCount)
    }

    @Test
    fun `ending session manually below time threshold fails`() {
        configService = FakeConfigService()
        createOrchestrator(false)
        clock.tick(1000)

        orchestrator.endSessionWithManual(true)
        assertEquals(1, sessionService.startTimestamps.size)
        assertEquals(0, sessionService.manualStartCount)
        assertEquals(0, sessionService.manualEndCount)
    }

    @Test
    fun `ending session manually when no session exists starts new session`() {
        configService = FakeConfigService()
        createOrchestrator(true)
        clock.tick(1000)

        orchestrator.endSessionWithManual(true)
        assertEquals(0, sessionService.startTimestamps.size)
        assertEquals(0, sessionService.manualStartCount)
        assertEquals(0, sessionService.manualEndCount)
    }

    private fun verifyPrepareEnvelopeCalled(expectedCount: Int = 1) {
        assertEquals(expectedCount, memoryCleanerService.callCount)
        val expectedPropCount = when (expectedCount) {
            0 -> 1
            else -> 0
        }
        assertEquals(expectedPropCount, sessionProperties.get().size)
    }

    private fun createOrchestrator(background: Boolean) {
        processStateService.listeners.clear()
        processStateService.isInBackground = background
        sessionService.startTimestamps.clear()
        backgroundActivityService.startTimestamps.clear()
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            sessionService,
            backgroundActivityService,
            clock,
            configService,
            memoryCleanerService,
            userService,
            ndkService,
            sessionProperties,
            internalErrorService
        )
    }
}
