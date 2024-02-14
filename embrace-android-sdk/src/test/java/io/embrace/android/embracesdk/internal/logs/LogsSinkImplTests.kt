package io.embrace.android.embracesdk.internal.logs

import io.mockk.mockk
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert
import org.junit.Before
import org.junit.Test

internal class LogsSinkImplTests {

    private lateinit var logsSink: LogsSink

    @Before
    fun setup() {
        logsSink = LogsSinkImpl()
    }

    @Test
    fun `verify default state`() {
        Assert.assertEquals(0, logsSink.logs().size)
        Assert.assertEquals(0, logsSink.flushLogs().size)
        Assert.assertEquals(
            CompletableResultCode.ofSuccess(),
            logsSink.storeLogs(listOf())
        )
    }

    @Test
    fun `flushing clears logs`() {
        logsSink.storeLogs(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        val snapshot = logsSink.logs()
        Assert.assertEquals(2, snapshot.size)

        val flushedLogs = logsSink.flushLogs()
        Assert.assertEquals(2, flushedLogs.size)
        repeat(2) {
            Assert.assertSame(snapshot[it], flushedLogs[it])
        }
        Assert.assertEquals(0, logsSink.logs().size)
    }
}