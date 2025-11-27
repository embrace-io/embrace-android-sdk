package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.fixtures.deferredLog
import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.fixtures.testLog
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
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

    @Before
    fun setUp() {
        sink = LogSinkImpl()
        impl = LogPayloadSourceImpl(sink)
    }

    @Test
    fun `getBatchedLogPayload returns a correct payload`() {
        sink.storeLogs(listOf(testLog))
        val payload = impl.getBatchedLogPayload()
        val log = checkNotNull(payload.logs?.single())
        assertEquals(0, sink.logsForNextBatch().size)
        assertEquals(1, payload.logs?.size)
        assertEquals(testLog, log)
    }

    @Test
    fun `log to with IMMEDIATE SendMode returns correctly`() {
        sink.storeLogs(listOf(sendImmediatelyLog))
        val payloads = impl.getSingleLogPayloads()
        val logRequest = checkNotNull(payloads.single())
        assertNull(sink.pollUnbatchedLog())
        assertEquals(LogPayload(logs = listOf(sendImmediatelyLog)), logRequest.payload)
        assertFalse(logRequest.defer)
    }

    @Test
    fun `log to with DEFER SendMode returns correctly`() {
        sink.storeLogs(listOf(deferredLog))
        val payloads = impl.getSingleLogPayloads()
        val logRequest = checkNotNull(payloads.single())
        assertNull(sink.pollUnbatchedLog())
        assertEquals(LogPayload(logs = listOf(deferredLog)), logRequest.payload)
        assertTrue(logRequest.defer)
    }

    @Test
    fun `getSingleLogPayloads returns the correct payload`() {
        sink.storeLogs(listOf(sendImmediatelyLog))
        val payloads = impl.getSingleLogPayloads()
        val log = checkNotNull(payloads.single())
        assertNull(sink.pollUnbatchedLog())
        assertEquals(LogPayload(logs = listOf(sendImmediatelyLog)), log.payload)
    }

    @Test
    fun `getSingleLogPayloads returns the maximum number of payloads`() {
        repeat(11) {
            sink.storeLogs(listOf(sendImmediatelyLog))
        }

        val payloads = impl.getSingleLogPayloads()
        assertEquals(10, payloads.size)
        assertNotNull(sink.pollUnbatchedLog())
        assertNull(sink.pollUnbatchedLog())
    }
}
