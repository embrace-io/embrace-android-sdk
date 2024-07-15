package io.embrace.android.embracesdk.capture.envelope.session

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeSessionPayloadSource
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SessionEnvelopeSourceImplTest {

    @Test
    fun getEnvelope() {
        val metadataSource = FakeEnvelopeMetadataSource()
        val resourceSource = FakeEnvelopeResourceSource()
        val sessionPayloadSource = FakeSessionPayloadSource()
        val source = SessionEnvelopeSourceImpl(
            metadataSource,
            resourceSource,
            sessionPayloadSource,
        )
        val payload = source.getEnvelope(SessionSnapshotType.NORMAL_END, true)
        assertEquals(metadataSource.metadata, payload.metadata)
        assertEquals(resourceSource.resource, payload.resource)
        assertEquals(sessionPayloadSource.sessionPayload, payload.data)
        assertEquals("spans", payload.type)
        assertEquals("0.1.0", payload.version)
    }
}
