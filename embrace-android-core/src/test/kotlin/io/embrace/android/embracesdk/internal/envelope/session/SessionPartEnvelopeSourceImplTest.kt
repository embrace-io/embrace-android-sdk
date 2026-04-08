package io.embrace.android.embracesdk.internal.envelope.session

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeSessionPartPayloadSource
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionPartSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SessionPartEnvelopeSourceImplTest {

    @Test
    fun getEnvelope() {
        val metadataSource = FakeEnvelopeMetadataSource()
        val resourceSource = FakeEnvelopeResourceSource()
        val partPayloadSource = FakeSessionPartPayloadSource()
        val source = SessionPartEnvelopeSourceImpl(
            metadataSource,
            resourceSource,
            partPayloadSource,
        )
        val payload = source.getEnvelope(SessionPartSnapshotType.NORMAL_END, true)
        assertEquals(metadataSource.metadata, payload.metadata)
        assertEquals(resourceSource.resource, payload.resource)
        assertEquals(partPayloadSource.sessionPayload, payload.data)
        assertEquals("spans", payload.type)
        assertEquals("0.1.0", payload.version)
    }
}
