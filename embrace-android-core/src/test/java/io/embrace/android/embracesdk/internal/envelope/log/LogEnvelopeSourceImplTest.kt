package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.internal.logs.LogRequest
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
        assertEquals(logSource.getBatchedLogPayload(), payload.data)
        assertEquals("logs", payload.type)
        assertEquals("0.1.0", payload.version)
    }

    @Test
    fun getSingleLogEnvelopes() {
        with(source.getSingleLogEnvelopes().single().payload) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertEquals(logSource.getSingleLogPayloads().single().payload, data)
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }

    @Test
    fun `check maximum number of envelopes returned`() {
        val fakeLogPayloadSource = FakeLogPayloadSource()
        val logRequests = mutableListOf<LogRequest<LogPayload>>()
        repeat(5) {
            logRequests.add(LogRequest(LogPayload(listOf(sendImmediatelyLog.copy(body = "$it")))))
        }

        fakeLogPayloadSource.singleLogPayloadsSource = logRequests
        val maxedOutSource = LogEnvelopeSourceImpl(
            metadataSource,
            resourceSource,
            fakeLogPayloadSource,
        )
        assertEquals(5, maxedOutSource.getSingleLogEnvelopes().size)
    }
}
