package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import org.junit.Assert.assertEquals
import org.junit.Test

internal class LogEnvelopeSourceImplTest {

    @Test
    fun getEnvelope() {
        val metadataSource = FakeEnvelopeMetadataSource()
        val resourceSource = FakeEnvelopeResourceSource()
        val logSource = FakeLogPayloadSource()
        val source = LogEnvelopeSourceImpl(
            metadataSource,
            resourceSource,
            logSource,
        )
        val payload = source.getEnvelope()
        assertEquals(metadataSource.metadata, payload.metadata)
        assertEquals(resourceSource.resource, payload.resource)
        assertEquals(logSource.logs, payload.data)
        assertEquals("logs", payload.type)
        assertEquals("0.1.0", payload.version)
    }
}
