package io.embrace.android.embracesdk.internal.crash

import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.concurrency.BlockableExecutorService
import io.embrace.android.embracesdk.worker.ExecutorName
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LastRunCrashVerifierTest {

    private lateinit var lastRunCrashVerifier: LastRunCrashVerifier
    private lateinit var mockCrashFileMarker: CrashFileMarker
    private lateinit var fakeWorkerThreadModule: FakeWorkerThreadModule
    private lateinit var executor: BlockableExecutorService

    @Before
    fun setUp() {
        mockCrashFileMarker = mockk()
        lastRunCrashVerifier = LastRunCrashVerifier(mockCrashFileMarker)
        fakeWorkerThreadModule = FakeWorkerThreadModule()
        executor = fakeWorkerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test calling didLastRunCrash() returns true if marker file exists`() {
        every { mockCrashFileMarker.getAndCleanMarker() } returns true
        assertTrue(lastRunCrashVerifier.didLastRunCrash())
        assertTrue(lastRunCrashVerifier.didLastRunCrash()) // check the result is cached
    }

    @Test
    fun `test calling didLastRunCrash() returns false if marker file does not exist`() {
        every { mockCrashFileMarker.getAndCleanMarker() } returns false
        assertFalse(lastRunCrashVerifier.didLastRunCrash())
        assertFalse(lastRunCrashVerifier.didLastRunCrash()) // check the result is cached
    }

    @Test
    fun `test calling readAndCleanMarkerAsync and then didLastRunCrash() returns true if marker file exists`() {
        every { mockCrashFileMarker.getAndCleanMarker() } returns true
        lastRunCrashVerifier.readAndCleanMarkerAsync(executor)
        assertTrue(lastRunCrashVerifier.didLastRunCrash())
    }

    @Test
    fun `test calling readAndCleanMarkerAsync and then didLastRunCrash() returns false if marker file doesn't exist`() {
        every { mockCrashFileMarker.getAndCleanMarker() } returns false
        lastRunCrashVerifier.readAndCleanMarkerAsync(executor)
        assertFalse(lastRunCrashVerifier.didLastRunCrash())
    }
}
