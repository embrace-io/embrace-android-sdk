package io.embrace.android.embracesdk.capture.envelope

import io.embrace.android.embracesdk.fakes.FakeEnvelopeMetadataSource
import io.embrace.android.embracesdk.fakes.FakeEnvelopeResourceSource
import io.embrace.android.embracesdk.fakes.FakeLogPayloadSource
import io.embrace.android.embracesdk.session.orchestrator.SessionSnapshotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class LogEnvelopeSourceTest {

    @Test
    fun getEnvelope() {
        val metadataSource = FakeEnvelopeMetadataSource()
        val resourceSource = FakeEnvelopeResourceSource()
        val logSource = FakeLogPayloadSource()
        val source = LogEnvelopeSource(
            metadataSource,
            resourceSource,
            logSource,
        )
        val payload = source.getEnvelope(SessionSnapshotType.NORMAL_END)
        assertEquals(metadataSource.metadata, payload.metadata)
        assertEquals(resourceSource.resource, payload.resource)
        assertEquals(logSource.logs, payload.data)

        // future fields that need populating:
        assertNull(payload.type)
        assertNull(payload.version)
    }
}
