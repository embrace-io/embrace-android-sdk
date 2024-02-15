package io.embrace.android.embracesdk.internal.logs

import io.mockk.mockk
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class LogSinkImpltest {
    private lateinit var logSink: LogSink

    @Before
    fun setup() {
        logSink = LogSinkImpl()
    }

    @Test
    fun `verify default state`() {
        assertEquals(0, logSink.logs().size)
        assertEquals(0, logSink.flushLogs().size)
        assertEquals(CompletableResultCode.ofSuccess(), logSink.storeLogs(listOf()))
    }

    @Test
    fun `flushing clears stored logs`() {
        logSink.storeLogs(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        val snapshot = logSink.logs()
        assertEquals(2, snapshot.size)

        val flushedLogs = logSink.flushLogs()
        assertEquals(2, flushedLogs.size)
        repeat(2) {
            assertSame(snapshot[it], flushedLogs[it])
        }
        assertEquals(0, logSink.logs().size)
    }
}