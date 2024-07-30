package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fixtures.nonbatchableLog
import io.embrace.android.embracesdk.fixtures.unbatchableLogRecordData
import io.embrace.android.embracesdk.internal.envelope.log.LogPayloadSourceImpl
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.payload.LogPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

internal class LogPayloadSourceImplTest {

    private lateinit var impl: LogPayloadSourceImpl
    private lateinit var sink: LogSinkImpl
    private val fakeLog = FakeLogRecordData()

    @Before
    fun setUp() {
        sink = LogSinkImpl().apply {
            storeLogs(listOf(fakeLog))
            storeLogs(listOf(unbatchableLogRecordData))
        }
        impl = LogPayloadSourceImpl(sink)
    }

    @Test
    fun `getBatchedLogPayload returns a correct payload`() {
        val payload = impl.getBatchedLogPayload()
        val log = checkNotNull(payload.logs?.single())
        assertEquals(0, sink.completedLogs().size)
        assertEquals(1, payload.logs?.size)
        assertEquals(fakeLog.timestampEpochNanos, log.timeUnixNano)
        assertEquals(fakeLog.severityText, log.severityText)
        assertEquals(fakeLog.severity.severityNumber, log.severityNumber)
        assertEquals(fakeLog.attributes.size(), log.attributes?.size)
        assertEquals(fakeLog.body.asString(), log.body)
    }

    @Test
    fun `getNonbatchedLogPayloads returns the correct payload`() {
        val payloads = impl.getNonbatchedLogPayloads()
        val log = checkNotNull(payloads.single())
        assertNull(sink.pollNonbatchedLog())
        assertEquals(LogPayload(logs = listOf(nonbatchableLog)), log)
    }

    @Test
    fun `getNonbatchedLogPayloads returns the maximum number of payloads`() {
        repeat(10) {
            sink.storeLogs(listOf(unbatchableLogRecordData))
        }

        val payloads = impl.getNonbatchedLogPayloads()
        assertEquals(10, payloads.size)
        assertNotNull(sink.pollNonbatchedLog())
        assertNull(sink.pollNonbatchedLog())
    }
}
