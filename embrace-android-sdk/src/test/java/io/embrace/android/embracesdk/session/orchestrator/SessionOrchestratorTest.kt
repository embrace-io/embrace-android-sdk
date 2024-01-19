package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.FakeSessionService
import io.embrace.android.embracesdk.config.remote.RemoteConfig
import io.embrace.android.embracesdk.fakes.FakeBackgroundActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import io.embrace.android.embracesdk.fakes.fakeSessionBehavior
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

    @Before
    fun setUp() {
        processStateService = FakeProcessStateService()
        sessionService = FakeSessionService()
        backgroundActivityService = FakeBackgroundActivityService()
        clock = FakeClock()
        configService = FakeConfigService(backgroundActivityCaptureEnabled = true)
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            sessionService,
            backgroundActivityService,
            clock,
            configService
        )
    }

    @Test
    fun `test initial behavior in background`() {
        createOrchestratorInBackground()
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(0, sessionService.startTimestamps.size)
        assertEquals(1, backgroundActivityService.startTimestamps.size)
    }

    @Test
    fun `test initial behavior in foreground`() {
        assertEquals(orchestrator, processStateService.listeners.single())
        assertEquals(1, sessionService.startTimestamps.size)
        assertEquals(0, backgroundActivityService.startTimestamps.size)
    }

    @Test
    fun `test on foreground call`() {
        createOrchestratorInBackground()
        orchestrator.onForeground(true, TIMESTAMP)
        assertEquals(TIMESTAMP, sessionService.startTimestamps.single())
        assertEquals(TIMESTAMP, backgroundActivityService.endTimestamps.single())
    }

    @Test
    fun `test on background call`() {
        orchestrator.onBackground(TIMESTAMP)
        assertEquals(TIMESTAMP, sessionService.endTimestamps.single())
        assertEquals(TIMESTAMP, backgroundActivityService.startTimestamps.single())
    }

    @Test
    fun `end session with manual in foreground`() {
        orchestrator.endSessionWithManual(true)
        assertEquals(1, sessionService.manualEndCount)
    }

    @Test
    fun `end session with manual in background`() {
        processStateService.isInBackground = true
        orchestrator.endSessionWithManual(true)
        assertEquals(0, sessionService.manualEndCount)
    }

    @Test
    fun `test background activity capture disabled`() {
        configService = FakeConfigService(backgroundActivityCaptureEnabled = false)
        createOrchestratorInBackground()
        orchestrator.onBackground(TIMESTAMP)
        assertTrue(backgroundActivityService.startTimestamps.isEmpty())
    }

    @Test
    fun `test session capture disabled`() {
        configService = FakeConfigService(
            sessionBehavior = fakeSessionBehavior {
                RemoteConfig(disabledMessageTypes = setOf("session"))
            }
        )
        createOrchestratorInBackground()
        orchestrator.onForeground(true, 0, TIMESTAMP)
        assertTrue(backgroundActivityService.startTimestamps.isEmpty())
    }

    private fun createOrchestratorInBackground() {
        processStateService.listeners.clear()
        processStateService.isInBackground = true
        sessionService.startTimestamps.clear()
        backgroundActivityService.startTimestamps.clear()
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            sessionService,
            backgroundActivityService,
            clock,
            configService
        )
    }
}
