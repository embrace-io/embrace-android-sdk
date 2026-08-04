package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.concurrency.SingleThreadTestScheduledExecutor
import io.embrace.android.embracesdk.fixtures.deferredLog
import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class LogSinkImplTest {
    private lateinit var logSink: LogSink

    @Before
    fun setup() {
        logSink = LogSinkImpl()
    }

    @Test
    fun `verify default state`() {
        assertEquals(0, logSink.logsForNextBatch().size)
        assertEquals(0, logSink.storedLogCount())
        assertEquals(0, logSink.flushBatch().size)
        assertEquals(StoreDataResult.SUCCESS, logSink.storeLogs(listOf()))
    }

    @Test
    fun `storing logs adds to stored logs`() {
        val resultCode = logSink.storeLogs(listOf(Log()))
        assertEquals(StoreDataResult.SUCCESS, resultCode)
        assertEquals(1, logSink.logsForNextBatch().size)
        assertEquals(Log(), logSink.logsForNextBatch().first())
    }

    @Test
    fun `flushing clears stored logs`() {
        logSink.storeLogs(listOf(Log(), Log()))
        val snapshot = logSink.logsForNextBatch()
        assertEquals(2, snapshot.size)

        val flushedLogs = logSink.flushBatch()
        assertEquals(2, flushedLogs.size)
        repeat(2) {
            assertSame(snapshot[it], flushedLogs[it])
        }
        assertEquals(0, logSink.logsForNextBatch().size)
    }

    @Test
    fun `flushing does not retain previously flushed logs`() {
        logSink.storeLogs(listOf(Log(body = "one"), Log(body = "two")))
        assertEquals(2, logSink.flushBatch().size)

        assertEquals(0, logSink.flushBatch().size)
        assertEquals(0, logSink.logsForNextBatch().size)

        logSink.storeLogs(listOf(Log(body = "three")))
        assertEquals(1, logSink.flushBatch().size)
    }

    @Test
    fun `flushing caps the batch and retains equal logs that were not flushed`() {
        val total = MAX_LOGS_PER_BATCH + 10
        logSink.storeLogs(List(total) { Log() })
        assertEquals(total, logSink.storedLogCount())

        // logs beyond the batch cap must survive the flush, even though they are all equal to each other
        assertEquals(MAX_LOGS_PER_BATCH, logSink.flushBatch().size)
        assertEquals(10, logSink.storedLogCount())
        assertEquals(10, logSink.flushBatch().size)
        assertEquals(0, logSink.flushBatch().size)
    }

    @Test
    fun `concurrent stores and flushes neither lose nor duplicate logs`() {
        val totalToStore = 5_000
        val storeDoneLatch = CountDownLatch(1)
        val flushed = ArrayList<Log>(totalToStore)

        // store logs one at a time from another thread
        val producer = SingleThreadTestScheduledExecutor()
        producer.submit {
            repeat(totalToStore) { i ->
                logSink.storeLogs(listOf(Log(body = "log$i")))
            }
            storeDoneLatch.countDown()
        }

        // repeatedly flush on this thread while the producer is storing
        while (storeDoneLatch.count > 0L) {
            flushed += logSink.flushBatch()
        }
        assertTrue(storeDoneLatch.await(5, TimeUnit.SECONDS))

        // drain anything stored after the last in-loop flush. each flush is capped at MAX_LOGS_PER_BATCH,
        // so keep going until the sink is empty.
        do {
            val batch = logSink.flushBatch()
            flushed += batch
        } while (batch.isNotEmpty())

        // every stored log should have been flushed exactly once
        assertEquals(0, logSink.storedLogCount())
        assertEquals(totalToStore, flushed.size)
        val distinctBodies = flushed.mapTo(HashSet()) { it.body }
        assertEquals(totalToStore, distinctBodies.size)
    }

    @Test
    fun `onStore is called when logs are stored`() {
        var onStoreCalled = false
        (logSink as LogSinkImpl).registerLogStoredCallback { onStoreCalled = true }
        logSink.storeLogs(listOf(Log()))
        assertEquals(true, onStoreCalled)
    }

    @Test
    fun `logs with IMMEDIATE SendMode are stored in priority log queue`() {
        val resultCode = logSink.storeLogs(listOf(sendImmediatelyLog))
        assertEquals(StoreDataResult.SUCCESS, resultCode)
        assertEquals(0, logSink.logsForNextBatch().size)
        val logRequest = checkNotNull(logSink.pollUnbatchedLog())
        assertEquals(sendImmediatelyLog, logRequest.payload)
        assertFalse(logRequest.defer)
        assertNull(logSink.pollUnbatchedLog())
    }

    @Test
    fun `logs with DEFER SendMode are stored in priority log queue`() {
        val resultCode = logSink.storeLogs(listOf(deferredLog))
        assertEquals(StoreDataResult.SUCCESS, resultCode)
        assertEquals(0, logSink.logsForNextBatch().size)
        val logRequest = checkNotNull(logSink.pollUnbatchedLog())
        assertEquals(deferredLog, logRequest.payload)
        assertTrue(logRequest.defer)
        assertNull(logSink.pollUnbatchedLog())
    }

    @Test
    fun `unbatchable logs are stored in the unbatched log queue`() {
        val resultCode = logSink.storeLogs(listOf(sendImmediatelyLog))
        assertEquals(StoreDataResult.SUCCESS, resultCode)
        assertEquals(0, logSink.logsForNextBatch().size)
        assertEquals(sendImmediatelyLog, checkNotNull(logSink.pollUnbatchedLog()).payload)
        assertNull(logSink.pollUnbatchedLog())
    }
}
