package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.concurrency.BlockingScheduledExecutorService
import io.embrace.android.embracesdk.fakes.OtelSdkMode
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadPool
import io.embrace.android.embracesdk.fakes.injection.TestInitServices
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LastRunCrashVerifierTest {

    private lateinit var lastRunCrashVerifier: LastRunCrashVerifier
    private lateinit var crashFileMarker: FakeCrashFileMarker
    private lateinit var fakeWorkerThreadPool: FakeWorkerThreadPool
    private lateinit var worker: BackgroundWorker
    private lateinit var executor: BlockingScheduledExecutorService

    @Before
    fun setUp() {
        crashFileMarker = FakeCrashFileMarker()
        lastRunCrashVerifier = LastRunCrashVerifier(crashFileMarker)
        fakeWorkerThreadPool =
            FakeWorkerThreadPool(
                fakeInitServices = TestInitServices(otelSdkMode = OtelSdkMode.COMPAT),
                testWorkers = listOf(Worker.Background.NonIoRegWorker),
            )
        worker = fakeWorkerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker)
        executor = fakeWorkerThreadPool.executorFor(Worker.Background.NonIoRegWorker)
    }

    @Test
    fun `test calling didLastRunCrash() returns true if marker file exists`() {
        crashFileMarker.marked = true
        assertTrue(lastRunCrashVerifier.didLastRunCrash())
        assertTrue(lastRunCrashVerifier.didLastRunCrash()) // check the result is cached
    }

    @Test
    fun `test calling didLastRunCrash() returns false if marker file does not exist`() {
        crashFileMarker.marked = false
        assertFalse(lastRunCrashVerifier.didLastRunCrash())
        assertFalse(lastRunCrashVerifier.didLastRunCrash()) // check the result is cached
    }

    @Test
    fun `test calling readAndCleanMarkerAsync and then didLastRunCrash() returns true if marker file exists`() {
        crashFileMarker.marked = true
        lastRunCrashVerifier.readAndCleanMarkerAsync(worker)
        executor.runCurrentlyBlocked()
        assertTrue(lastRunCrashVerifier.didLastRunCrash())
        assertEquals(1, crashFileMarker.getAndCleanCount)
    }

    @Test
    fun `test calling readAndCleanMarkerAsync and then didLastRunCrash() returns false if marker file doesn't exist`() {
        crashFileMarker.marked = false
        lastRunCrashVerifier.readAndCleanMarkerAsync(worker)
        executor.runCurrentlyBlocked()
        assertFalse(lastRunCrashVerifier.didLastRunCrash())
        assertEquals(1, crashFileMarker.getAndCleanCount)
    }
}

private class FakeCrashFileMarker : CrashFileMarker {
    var marked = false
    var getAndCleanCount = 0

    override fun getAndCleanMarker(): Boolean {
        getAndCleanCount++
        return marked
    }

    override fun mark() = Unit
    override fun removeMark() = Unit
    override fun isMarked(): Boolean = marked
    override fun handleCrash(crashId: String) = Unit
}
