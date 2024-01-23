package io.embrace.android.embracesdk.session.orchestrator

import io.embrace.android.embracesdk.FakeSessionService
import io.embrace.android.embracesdk.fakes.FakeBackgroundActivityService
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeInternalErrorService
import io.embrace.android.embracesdk.fakes.FakeMemoryCleanerService
import io.embrace.android.embracesdk.fakes.FakeProcessStateService
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

    @Before
    fun setUp() {
        processStateService = FakeProcessStateService()
        sessionService = FakeSessionService()
        backgroundActivityService = FakeBackgroundActivityService()
        clock = FakeClock()
        configService = FakeConfigService(backgroundActivityCaptureEnabled = true)
        memoryCleanerService = FakeMemoryCleanerService()
        internalErrorService = FakeInternalErrorService()
        orchestrator = SessionOrchestratorImpl(
            processStateService,
            sessionService,
            backgroundActivityService,
            clock,
            configService,
            memoryCleanerService,
            internalErrorService
        )
    }

    @Test
    fun `test initial behavior in background`() {
        createOrchestratorInBackground()
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
        createOrchestratorInBackground()
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
        createOrchestratorInBackground()
        orchestrator.onBackground(TIMESTAMP)
        verifyPrepareEnvelopeCalled()
        assertTrue(backgroundActivityService.startTimestamps.isEmpty())
    }

    private fun verifyPrepareEnvelopeCalled(expectedCount: Int = 1) {
        assertEquals(expectedCount, memoryCleanerService.callCount)
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
            configService,
            memoryCleanerService,
            internalErrorService
        )
    }
}
