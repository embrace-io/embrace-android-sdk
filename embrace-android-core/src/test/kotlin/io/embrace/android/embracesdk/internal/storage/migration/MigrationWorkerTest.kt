package io.embrace.android.embracesdk.internal.storage.migration

import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class MigrationWorkerTest {

    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var worker: MigrationWorker

    @Before
    fun setUp() {
        EmbTrace.durationTracker.reset()
        errorHandler = FakeErrorHandler(throwOnSdkError = false)
        worker = MigrationWorker(errorHandler)
    }

    @After
    fun tearDown() {
        EmbTrace.durationTracker.reset()
    }

    @Test
    fun `await returns after the job completes`() {
        val release = CountDownLatch(1)
        val handle = worker.submit("config", false, SdkErrorType.StorageMigrationFail) {
            release.await(TIMEOUT_SECS, TimeUnit.SECONDS)
        }
        val awaited = AtomicReference<Boolean>()
        val waiter = Thread { awaited.set(handle.await()) }.apply { start() }

        waiter.join(BLOCKED_MS)
        assertTrue(waiter.isAlive)

        release.countDown()
        waiter.join(TIMEOUT_MS)
        assertEquals(true, awaited.get())
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `await returns the default after a job failure`() {
        val handle = worker.submit<Boolean>("prefs", false, SdkErrorType.PrefsMigrationFail) {
            error("boom")
        }
        assertFalse(handle.await())
        assertEquals(SdkErrorType.PrefsMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
    }

    @Test
    fun `complete releases waiters for a job that was never submitted`() {
        val handle = MigrationHandle("telemetry", false, errorHandler)
        val awaited = AtomicReference<Boolean>()
        val waiter = Thread { awaited.set(handle.await()) }.apply { start() }

        waiter.join(BLOCKED_MS)
        assertTrue(waiter.isAlive)

        handle.complete()
        waiter.join(TIMEOUT_MS)
        assertEquals(false, awaited.get())
    }

    @Test
    fun `timed await returns the default and tracks the timeout`() {
        val release = CountDownLatch(1)
        val handle = worker.submit("config", false, SdkErrorType.StorageMigrationFail) {
            release.await(TIMEOUT_SECS, TimeUnit.SECONDS)
        }

        assertFalse(handle.await(BLOCKED_MS, SdkErrorType.StorageMigrationFail))
        assertFalse(handle.isComplete)
        assertEquals(SdkErrorType.StorageMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)

        release.countDown()
        assertTrue(handle.await())
        assertTrue(handle.isComplete)
    }

    @Test
    fun `timed await returns the result when the job completes in time`() {
        val handle = worker.submit("config", false, SdkErrorType.StorageMigrationFail) { true }
        assertTrue(handle.await(TIMEOUT_MS, SdkErrorType.StorageMigrationFail))
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `only the first completion takes effect`() {
        val handle = MigrationHandle("telemetry", 0, errorHandler)
        handle.complete(1)
        handle.complete(2)
        worker.submit(handle, SdkErrorType.StorageMigrationFail) { 3 }
        worker.submit("barrier", Unit, SdkErrorType.StorageMigrationFail) {}.await()
        assertEquals(1, handle.await())
    }

    @Test
    fun `jobs run in submission order on one thread`() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val threads = Collections.synchronizedSet(mutableSetOf<Thread>())
        val handles = listOf("a", "b", "c").map { name ->
            worker.submit<Unit>(name, Unit, SdkErrorType.StorageMigrationFail) {
                order.add(name)
                threads.add(Thread.currentThread())
            }
        }
        handles.forEach { it.await() }

        assertEquals(listOf("a", "b", "c"), order)
        val thread = threads.single()
        assertEquals("emb-migration", thread.name)
        assertTrue(thread.isDaemon)
    }

    @Test
    fun `thread is started on first submit`() {
        val created = AtomicBoolean(false)
        val lazyWorker = MigrationWorker(
            errorHandler,
            ThreadFactory { runnable ->
                created.set(true)
                Thread(runnable).apply { isDaemon = true }
            },
        )
        assertFalse(created.get())
        lazyWorker.submit("config", Unit, SdkErrorType.StorageMigrationFail) {}.await()
        assertTrue(created.get())
    }

    @Test
    fun `idle thread exits and a later job starts a new one`() {
        val threads = Collections.synchronizedList(mutableListOf<Thread>())
        val idleWorker = MigrationWorker(
            errorHandler,
            ThreadFactory { runnable ->
                Thread(runnable).apply { isDaemon = true }.also(threads::add)
            },
            keepAliveMs = 10,
        )
        idleWorker.submit("config", Unit, SdkErrorType.StorageMigrationFail) {}.await()
        val first = threads.single()
        first.join(TIMEOUT_MS)
        assertFalse(first.isAlive)

        assertTrue(idleWorker.submit("telemetry", false, SdkErrorType.StorageMigrationFail) { true }.await())
        assertEquals(2, threads.size)
    }

    @Test
    fun `a job that cannot be queued completes with the default`() {
        val failingWorker = MigrationWorker(errorHandler, ThreadFactory { error("no threads") })
        val handle = failingWorker.submit("config", false, SdkErrorType.StorageMigrationFail) { true }
        assertFalse(handle.await())
        assertEquals(SdkErrorType.StorageMigrationFail.toString(), errorHandler.sdkErrorMessages.single().msg)
    }

    @Test
    fun `interrupted await returns the default and keeps the interrupt`() {
        val handle = MigrationHandle("telemetry", false, errorHandler)
        val awaited = AtomicReference<Boolean>()
        val interrupted = AtomicBoolean(false)
        val waiter = Thread {
            Thread.currentThread().interrupt()
            awaited.set(handle.await())
            interrupted.set(Thread.currentThread().isInterrupted)
        }.apply { start() }

        waiter.join(TIMEOUT_MS)
        assertEquals(false, awaited.get())
        assertTrue(interrupted.get())
        assertTrue(errorHandler.sdkErrorMessages.isEmpty())
    }

    @Test
    fun `job and await are traced, with a duration unless disabled`() {
        worker.submit("config", Unit, SdkErrorType.StorageMigrationFail) {}.await()
        val durations = EmbTrace.durationTracker.flush()
        assertTrue("migration-config" in durations)
        assertTrue("migration-await-config" in durations)

        val untimed = MigrationHandle("prefetch", Unit, errorHandler, recordDuration = false)
        worker.submit(untimed, SdkErrorType.StorageMigrationFail) {}
        untimed.await()
        assertTrue(EmbTrace.durationTracker.flush().isEmpty())
    }

    private companion object {
        const val TIMEOUT_SECS = 5L
        const val TIMEOUT_MS = 5_000L
        const val BLOCKED_MS = 100L
    }
}
