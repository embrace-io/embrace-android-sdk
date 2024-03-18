package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import org.junit.Assert.assertEquals
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
        }
        impl = LogPayloadSourceImpl(sink)
    }

    @Test
    fun `test getLogPayload returns a correct payload`() {
        val payload = impl.getLogPayload()
        val log = checkNotNull(payload.logs?.single())
        assertEquals(0, sink.completedLogs().size)
        assertEquals(1, payload.logs?.size)
        assertEquals(fakeLog.timestampEpochNanos, log.timeUnixNano)
        assertEquals(fakeLog.severityText, log.severityText)
        assertEquals(fakeLog.severity.severityNumber, log.severityNumber)
        assertEquals(fakeLog.attributes.size(), log.attributes?.size)
        assertEquals(fakeLog.body.asString(), log.body?.message)
    }
}
