package io.embrace.android.embracesdk.internal.logs

import io.embrace.android.embracesdk.fixtures.testLog
import io.mockk.every
import io.mockk.mockk
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.Body
import io.opentelemetry.sdk.logs.data.LogRecordData
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
        assertEquals(0, logSink.completedLogs().size)
        assertEquals(0, logSink.flushLogs().size)
        assertEquals(CompletableResultCode.ofSuccess(), logSink.storeLogs(listOf()))
    }

    @Test
    fun `storing logs adds to stored logs`() {
        val resultCode = logSink.storeLogs(listOf(getLogRecordData()))
        assertEquals(CompletableResultCode.ofSuccess(), resultCode)
        assertEquals(1, logSink.completedLogs().size)
        assertEquals(EmbraceLogRecordData(logRecordData = getLogRecordData()), logSink.completedLogs().first())
    }

    @Test
    fun `flushing clears stored logs`() {
        logSink.storeLogs(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        val snapshot = logSink.completedLogs()
        assertEquals(2, snapshot.size)

        val flushedLogs = logSink.flushLogs()
        assertEquals(2, flushedLogs.size)
        repeat(2) {
            assertSame(snapshot[it], flushedLogs[it])
        }
        assertEquals(0, logSink.completedLogs().size)
    }

    private fun getLogRecordData(): LogRecordData {
        return mockk {
            every { severityText } returns testLog.severityText
            every { severity } returns Severity.INFO
            every { body } returns Body.string(testLog.body.message)
            val attrBuilder = Attributes.builder()
            testLog.attributes.forEach { (key, value) ->
                attrBuilder.put(AttributeKey.stringKey(key), value as String)
            }
            every { attributes } returns attrBuilder.build()
            every { spanContext } returns mockk {
                every { traceId } returns testLog.traceId
                every { spanId } returns testLog.spanId
            }
            every { observedTimestampEpochNanos } returns testLog.timeUnixNanos
        }
    }
}
