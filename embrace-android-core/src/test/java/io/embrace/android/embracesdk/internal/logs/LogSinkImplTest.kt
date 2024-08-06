package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fixtures.unbatchableLogRecordData
import io.embrace.android.embracesdk.internal.payload.toNewPayload
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert
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
        Assert.assertEquals(0, logSink.completedLogs().size)
        Assert.assertEquals(0, logSink.flushLogs().size)
        Assert.assertEquals(CompletableResultCode.ofSuccess(), logSink.storeLogs(listOf()))
    }

    @Test
    fun `storing logs adds to stored logs`() {
        val resultCode = logSink.storeLogs(listOf(FakeLogRecordData()))
        Assert.assertEquals(CompletableResultCode.ofSuccess(), resultCode)
        Assert.assertEquals(1, logSink.completedLogs().size)
        Assert.assertEquals(FakeLogRecordData().toNewPayload(), logSink.completedLogs().first())
    }

    @Test
    fun `flushing clears stored logs`() {
        logSink.storeLogs(listOf(FakeLogRecordData(), FakeLogRecordData()))
        val snapshot = logSink.completedLogs()
        Assert.assertEquals(2, snapshot.size)

        val flushedLogs = logSink.flushLogs()
        Assert.assertEquals(2, flushedLogs.size)
        repeat(2) {
            Assert.assertSame(snapshot[it], flushedLogs[it])
        }
        Assert.assertEquals(0, logSink.completedLogs().size)
    }

    @Test
    fun `onStore is called when logs are stored`() {
        var onStoreCalled = false
        (logSink as LogSinkImpl).registerLogStoredCallback { onStoreCalled = true }
        logSink.storeLogs(listOf(FakeLogRecordData()))
        Assert.assertEquals(true, onStoreCalled)
    }

    @Test
    fun `unbatchable logs are stored in priority log queue`() {
        val resultCode = logSink.storeLogs(listOf(unbatchableLogRecordData))
        Assert.assertEquals(CompletableResultCode.ofSuccess(), resultCode)
        Assert.assertEquals(0, logSink.completedLogs().size)
        Assert.assertEquals(unbatchableLogRecordData.log, logSink.pollNonbatchedLog())
        Assert.assertNull(logSink.pollNonbatchedLog())
    }
}
