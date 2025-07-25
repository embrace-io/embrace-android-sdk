package io.embrace.android.embracesdk.internal.otel.logs

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

internal class LogSinkImplTest {
    private lateinit var logSink: LogSink

    @Before
    fun setup() {
        logSink = LogSinkImpl()
    }

    @Test
    fun `verify default state`() {
        assertEquals(0, logSink.logsForNextBatch().size)
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
