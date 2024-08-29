package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fixtures.unbatchableLogRecordData
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
        assertEquals(0, logSink.completedLogs().size)
        assertEquals(0, logSink.flushLogs().size)
        assertEquals(CompletableResultCode.ofSuccess(), logSink.storeLogs(listOf()))
    }

    @Test
    fun `storing logs adds to stored logs`() {
        val resultCode = logSink.storeLogs(listOf(FakeLogRecordData()))
        assertEquals(CompletableResultCode.ofSuccess(), resultCode)
        assertEquals(1, logSink.completedLogs().size)
        assertEquals(FakeLogRecordData().toNewPayload(), logSink.completedLogs().first())
    }

    @Test
    fun `flushing clears stored logs`() {
        logSink.storeLogs(listOf(FakeLogRecordData(), FakeLogRecordData()))
        val snapshot = logSink.completedLogs()
        assertEquals(2, snapshot.size)

        val flushedLogs = logSink.flushLogs()
        assertEquals(2, flushedLogs.size)
        repeat(2) {
            assertSame(snapshot[it], flushedLogs[it])
        }
        assertEquals(0, logSink.completedLogs().size)
    }

    @Test
    fun `onStore is called when logs are stored`() {
        var onStoreCalled = false
        (logSink as LogSinkImpl).registerLogStoredCallback { onStoreCalled = true }
        logSink.storeLogs(listOf(FakeLogRecordData()))
        assertEquals(true, onStoreCalled)
    }

    @Test
    fun `unbatchable logs are stored in priority log queue`() {
        val resultCode = logSink.storeLogs(listOf(unbatchableLogRecordData))
        assertEquals(CompletableResultCode.ofSuccess(), resultCode)
        assertEquals(0, logSink.completedLogs().size)
        assertEquals(unbatchableLogRecordData.log, logSink.pollNonbatchedLog())
        assertNull(logSink.pollNonbatchedLog())
    }
}
