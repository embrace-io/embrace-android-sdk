package io.embrace.android.embracesdk.internal.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredTelemetryMetadataTest {

    companion object {
        private const val TIMESTAMP = 1726739283136
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val PROCESS_ID = "fakeProcessId"
    }

    private val typeNameMap = mapOf(
        SupportedEnvelopeType.CRASH to "p1",
        SupportedEnvelopeType.SESSION to "p3",
        SupportedEnvelopeType.LOG to "p5",
        SupportedEnvelopeType.BLOB to "p7"
    )

    @Test
    fun `construct objects`() {
        typeNameMap.entries.forEach { (type, priority) ->
            listOf(true, false).forEach { payloadComplete ->
                assertEquals(
                    "${priority}_${TIMESTAMP}_${UUID}_${PROCESS_ID}_${payloadComplete}_aei_v1.json",
                    StoredTelemetryMetadata(
                        TIMESTAMP,
                        UUID,
                        PROCESS_ID,
                        type,
                        payloadComplete,
                        payloadType = PayloadType.AEI
                    ).filename
                )
            }
        }
    }

    @Test
    fun `from invalid filename`() {
        val badFilenames = listOf(
            "",
            "foo",
            "my_session.json",
            "1234567890_session_v1.json",
            "p4_${TIMESTAMP}_b_c_true_v1.json",
            "p1_b_c_false_v1.json",
            "p3_${TIMESTAMP}_b_c_d_v1.json",
            "p3_${TIMESTAMP}_c_d_v1.json"
        )
        badFilenames.forEach { filename ->
            val result = StoredTelemetryMetadata.fromFilename(filename)
            assertTrue("Filename failed: $filename", result.isFailure)
        }
    }

    @Test
    fun `from valid filename`() {
        typeNameMap.entries.forEach { (type, priority) ->
            listOf(true, false).forEach { payloadComplete ->
                val input = "${priority}_${TIMESTAMP}_${UUID}_${PROCESS_ID}_${payloadComplete}_native_v1.json"
                with(StoredTelemetryMetadata.fromFilename(input).getOrThrow()) {
                    assertEquals(input, filename)
                    assertEquals(TIMESTAMP, timestamp)
                    assertEquals(UUID, uuid)
                    assertEquals(type, envelopeType)
                    assertEquals(payloadComplete, complete)
                    assertEquals(PayloadType.NATIVE_CRASH, payloadType)
                }
            }
        }
    }
}
