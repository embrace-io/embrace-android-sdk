package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.fixtures.nonbatchableLog
import io.embrace.android.embracesdk.internal.capture.envelope.log.LogEnvelopeSourceImpl
import io.embrace.android.embracesdk.internal.payload.LogPayload
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LogEnvelopeSourceImplTest {

    private val metadataSource = FakeEnvelopeMetadataSource()
    private val resourceSource = FakeEnvelopeResourceSource()
    private val logSource = FakeLogPayloadSource()
    private val source = LogEnvelopeSourceImpl(
        metadataSource,
        resourceSource,
        logSource,
    )

    @Test
    fun getBatchedLogEnvelope() {
        val payload = source.getBatchedLogEnvelope()
        assertEquals(metadataSource.metadata, payload.metadata)
        assertEquals(resourceSource.resource, payload.resource)
        assertEquals(logSource.logs, payload.data)
        assertEquals("logs", payload.type)
        assertEquals("0.1.0", payload.version)
    }

    @Test
    fun getNonbatchedLogEnvelopes() {
        with(source.getNonbatchedEnvelope().single()) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertEquals(logSource.nonbatchedLogs.single(), data)
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }

    @Test
    fun `check maximum number of envelopes returned`() {
        val fakeLogPayloadSource = FakeLogPayloadSource()
        val nonbatchedLogs = mutableListOf<LogPayload>()
        repeat(5) {
            nonbatchedLogs.add(LogPayload(listOf(nonbatchableLog.copy(body = "$it"))))
        }

        fakeLogPayloadSource.nonbatchedLogs = nonbatchedLogs
        val maxedOutSource = LogEnvelopeSourceImpl(
            metadataSource,
            resourceSource,
            fakeLogPayloadSource,
        )
        assertEquals(5, maxedOutSource.getNonbatchedEnvelope().size)
    }
}
