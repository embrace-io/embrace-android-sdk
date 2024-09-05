package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fixtures.deferredLog
import io.embrace.android.embracesdk.fixtures.deferredLogRecordData
import io.embrace.android.embracesdk.fixtures.nonbatchableLog
import io.embrace.android.embracesdk.fixtures.unbatchableLogRecordData
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.payload.LogPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LogPayloadSourceImplTest {

    private lateinit var impl: LogPayloadSourceImpl
    private lateinit var sink: LogSinkImpl
    private val fakeLog = FakeLogRecordData()

    @Before
    fun setUp() {
        sink = LogSinkImpl()
        impl = LogPayloadSourceImpl(sink)
    }

    @Test
    fun `getBatchedLogPayload returns a correct payload`() {
        sink.storeLogs(listOf(fakeLog))
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
    fun `log to with IMMEDIATE SendMode returns correctly`() {
        sink.storeLogs(listOf(unbatchableLogRecordData))
        val payloads = impl.getNonbatchedLogPayloads()
        val logRequest = checkNotNull(payloads.single())
        assertNull(sink.pollNonbatchedLog())
        assertEquals(LogPayload(logs = listOf(nonbatchableLog)), logRequest.payload)
        assertFalse(logRequest.defer)
    }

    @Test
    fun `log to with DEFER SendMode returns correctly`() {
        sink.storeLogs(listOf(deferredLogRecordData))
        val payloads = impl.getNonbatchedLogPayloads()
        val logRequest = checkNotNull(payloads.single())
        assertNull(sink.pollNonbatchedLog())
        assertEquals(LogPayload(logs = listOf(deferredLog)), logRequest.payload)
        assertTrue(logRequest.defer)
    }

    @Test
    fun `getNonbatchedLogPayloads returns the correct payload`() {
        sink.storeLogs(listOf(unbatchableLogRecordData))
        val payloads = impl.getNonbatchedLogPayloads()
        val log = checkNotNull(payloads.single())
        assertNull(sink.pollNonbatchedLog())
        assertEquals(LogPayload(logs = listOf(nonbatchableLog)), log.payload)
    }

    @Test
    fun `getNonbatchedLogPayloads returns the maximum number of payloads`() {
        repeat(11) {
            sink.storeLogs(listOf(unbatchableLogRecordData))
        }

        val payloads = impl.getNonbatchedLogPayloads()
        assertEquals(10, payloads.size)
        assertNotNull(sink.pollNonbatchedLog())
        assertNull(sink.pollNonbatchedLog())
    }
}
