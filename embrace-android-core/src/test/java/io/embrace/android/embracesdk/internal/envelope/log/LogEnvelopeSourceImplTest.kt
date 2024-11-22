package io.embrace.android.embracesdk.internal.envelope.log

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.fixtures.sendImmediatelyLog
import io.embrace.android.embracesdk.internal.logs.LogRequest
import io.embrace.android.embracesdk.internal.payload.LogPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class LogEnvelopeSourceImplTest {

    private lateinit var metadataSource: FakeEnvelopeMetadataSource
    private lateinit var resourceSource: FakeEnvelopeResourceSource
    private lateinit var logSource: FakeLogPayloadSource
    private lateinit var logEnvelopeSource: LogEnvelopeSourceImpl

    @Before
    fun setup() {
        metadataSource = FakeEnvelopeMetadataSource()
        resourceSource = FakeEnvelopeResourceSource()
        logSource = FakeLogPayloadSource()
        logEnvelopeSource = LogEnvelopeSourceImpl(
            metadataSource,
            resourceSource,
            logSource,
        )
    }

    @Test
    fun getBatchedLogEnvelope() {
        logSource.singleLogPayloadsSource = mutableListOf<LogRequest<LogPayload>>().apply {
            repeat(5) {
                add(LogRequest(LogPayload(listOf(sendImmediatelyLog.copy(body = "$it")))))
            }
        }
        with(logEnvelopeSource.getBatchedLogEnvelope()) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertEquals(logSource.getBatchedLogPayload(), data)
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }

    @Test
    fun getSingleLogEnvelopes() {
        with(logEnvelopeSource.getSingleLogEnvelopes().single().payload) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertEquals(logSource.getSingleLogPayloads().single().payload, data)
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }

    @Test
    fun `check empty log envelope`() {
        with(logEnvelopeSource.getEmptySingleLogEnvelope()) {
            assertEquals(metadataSource.metadata, metadata)
            assertEquals(resourceSource.resource, resource)
            assertTrue(checkNotNull(data.logs).isEmpty())
            assertEquals("logs", type)
            assertEquals("0.1.0", version)
        }
    }
}
