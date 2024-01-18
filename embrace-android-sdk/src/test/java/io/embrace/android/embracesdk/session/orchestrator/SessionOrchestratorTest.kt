package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.FakeSessionService
import io.embrace.android.embracesdk.fakes.FakeBackgroundActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
import org.junit.Assert.assertEquals
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

    @Before
    fun setUp() {
        processStateService = FakeProcessStateService()
        sessionService = FakeSessionService()
        backgroundActivityService = FakeBackgroundActivityService()
        clock = FakeClock()
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            sessionService,
            backgroundActivityService,
            clock
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
        orchestrator.onForeground(true, 0, TIMESTAMP)
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

    private fun createOrchestratorInBackground() {
        processStateService.listeners.clear()
        processStateService.isInBackground = true
        sessionService.startTimestamps.clear()
        backgroundActivityService.startTimestamps.clear()
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            sessionService,
            backgroundActivityService,
            clock
        )
    }
}
