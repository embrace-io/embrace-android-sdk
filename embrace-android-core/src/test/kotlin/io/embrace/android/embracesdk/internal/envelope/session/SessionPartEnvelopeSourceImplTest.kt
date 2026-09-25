package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeSessionPartPayloadSource
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SessionPartEnvelopeSourceImplTest {

    private val metadataSource = FakeEnvelopeMetadataSource()
    private val resourceSource = FakeEnvelopeResourceSource()
    private val partPayloadSource = FakeSessionPartPayloadSource()
    private val source = SessionPartEnvelopeSourceImpl(
        metadataSource,
        resourceSource,
        partPayloadSource,
    )

    @Test
    fun getEnvelope() {
        val payload = source.getEnvelope(SessionPartSnapshotType.NORMAL_END, true)
        assertEquals(metadataSource.metadata, payload.metadata)
        assertEquals(resourceSource.resource, payload.resource)
        assertEquals(partPayloadSource.sessionPayload, payload.data)
        assertEquals("spans", payload.type)
        assertEquals("0.1.0", payload.version)
        assertEquals(1, partPayloadSource.payloadBuiltCount)
    }

    @Test
    fun `ending a session part builds neither a resource nor metadata`() {
        source.endSessionPart(SessionPartSnapshotType.NORMAL_END, true)
        assertEquals(1, partPayloadSource.endedWithoutPayloadCount)
        assertEquals(0, partPayloadSource.payloadBuiltCount)
        assertEquals(0, resourceSource.readCount)
        assertEquals(0, metadataSource.readCount)
    }
}
