package io.embrace.android.embracesdk.internal.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredTelemetryMetadataTest {

    companion object {
        private const val TIMESTAMP = 1726739283136
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
    }

    private val typeNameMap = mapOf(
        SupportedEnvelopeType.CRASH to "crash",
        SupportedEnvelopeType.SESSION to "session",
        SupportedEnvelopeType.LOG to "log",
        SupportedEnvelopeType.NETWORK to "network"
    )

    @Test
    fun `construct objects`() {
        typeNameMap.entries.forEach { (type, description) ->
            assertEquals(
                "${TIMESTAMP}_${description}_${UUID}_v1.json",
                StoredTelemetryMetadata(TIMESTAMP, UUID, type).filename
            )
        }
    }

    @Test
    fun `from invalid filename`() {
        val badFilenames = listOf(
            "",
            "foo",
            "my_session.json",
            "1234567890_session_v1.json",
            "a_b_c_v1.json",
            "${TIMESTAMP}_b_c_v1.json"
        )
        badFilenames.forEach { filename ->
            val result = StoredTelemetryMetadata.fromFilename(filename)
            assertTrue("Filename failed: $filename", result.isFailure)
        }
    }

    @Test
    fun `from valid filename`() {
        typeNameMap.entries.forEach { (type, description) ->
            val input = "${TIMESTAMP}_${description}_${UUID}_v1.json"
            with(StoredTelemetryMetadata.fromFilename(input).getOrThrow()) {
                assertEquals(input, filename)
                assertEquals(TIMESTAMP, timestamp)
                assertEquals(UUID, uuid)
                assertEquals(type, this.envelopeType)
            }
        }
    }
}
