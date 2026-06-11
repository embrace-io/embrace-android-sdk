package io.embrace.android.embracesdk.internal.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredTelemetryMetadataTest {

    companion object {
        private const val TIMESTAMP = 1726739283136
        private const val UUID = "c2610cd1-389f-422a-bfbc-25312c7a599a"
        private const val PROCESS_ID = "fakeProcessId"
        private const val USER_SESSION_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val SESSION_PART_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }

    private val typeNameMap = mapOf(
        SupportedEnvelopeType.CRASH to "p1",
        SupportedEnvelopeType.SESSION to "p3",
        SupportedEnvelopeType.LOG to "p5",
        SupportedEnvelopeType.BLOB to "p7"
    )

    @Test
    fun `construct objects with session ids`() {
        typeNameMap.entries.forEach { (type, priority) ->
            listOf(true, false).forEach { payloadComplete ->
                assertEquals(
                    "${priority}_${TIMESTAMP}_${UUID}_${PROCESS_ID}_${payloadComplete}_aei_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
                    StoredTelemetryMetadata(
                        timestamp = TIMESTAMP,
                        uuid = UUID,
                        processIdentifier = PROCESS_ID,
                        envelopeType = type,
                        complete = payloadComplete,
                        payloadType = PayloadType.AEI,
                        userSessionId = USER_SESSION_ID,
                        sessionPartId = SESSION_PART_ID,
                    ).filename
                )
            }
        }
    }

    @Test
    fun `construct objects with empty session ids encodes none`() {
        typeNameMap.entries.forEach { (type, priority) ->
            listOf(true, false).forEach { payloadComplete ->
                assertEquals(
                    "${priority}_${TIMESTAMP}_${UUID}_${PROCESS_ID}_${payloadComplete}_aei_none_none_v2.json",
                    StoredTelemetryMetadata(
                        timestamp = TIMESTAMP,
                        uuid = UUID,
                        processIdentifier = PROCESS_ID,
                        envelopeType = type,
                        complete = payloadComplete,
                        payloadType = PayloadType.AEI,
                    ).filename
                )
            }
        }
    }

    @Test
    fun `construct objects with only one empty session id`() {
        val filename = StoredTelemetryMetadata(
            timestamp = TIMESTAMP,
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = SupportedEnvelopeType.SESSION,
            payloadType = PayloadType.SESSION,
            userSessionId = USER_SESSION_ID,
            sessionPartId = "",
        ).filename
        assertEquals(
            "p3_${TIMESTAMP}_${UUID}_${PROCESS_ID}_true_session_${USER_SESSION_ID}_none_v2.json",
            filename
        )
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
            "p3_${TIMESTAMP}_c_d_v1.json",
            "p3_${TIMESTAMP}_${UUID}_${PROCESS_ID}_true_session_$USER_SESSION_ID" + "_v3.json",
            "p3_${TIMESTAMP}_${UUID}_${PROCESS_ID}_true_session_${USER_SESSION_ID}_v2.json",
            "p9_${TIMESTAMP}_${UUID}_${PROCESS_ID}_true_session_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
            "p3_notATimestamp_${UUID}_${PROCESS_ID}_true_session_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
            "p3_${TIMESTAMP}_${UUID}_${PROCESS_ID}_notABool_session_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json",
        )
        badFilenames.forEach { filename ->
            val result = StoredTelemetryMetadata.fromFilename(filename)
            assertTrue("Filename should fail: $filename", result.isFailure)
        }
    }

    @Test
    fun `from valid v1 filename decodes with empty session ids`() {
        typeNameMap.entries.forEach { (type, priority) ->
            listOf(true, false).forEach { payloadComplete ->
                val input = "${priority}_${TIMESTAMP}_${UUID}_${PROCESS_ID}_${payloadComplete}_native_v1.json"
                with(StoredTelemetryMetadata.fromFilename(input).getOrThrow()) {
                    assertEquals(TIMESTAMP, timestamp)
                    assertEquals(UUID, uuid)
                    assertEquals(PROCESS_ID, processIdentifier)
                    assertEquals(type, envelopeType)
                    assertEquals(payloadComplete, complete)
                    assertEquals(PayloadType.NATIVE_CRASH, payloadType)
                    assertEquals("", userSessionId)
                    assertEquals("", sessionPartId)
                }
            }
        }
    }

    @Test
    fun `from valid v2 filename`() {
        typeNameMap.entries.forEach { (type, priority) ->
            listOf(true, false).forEach { payloadComplete ->
                val input =
                    "${priority}_${TIMESTAMP}_${UUID}_${PROCESS_ID}_${payloadComplete}_native_${USER_SESSION_ID}_${SESSION_PART_ID}_v2.json"
                with(StoredTelemetryMetadata.fromFilename(input).getOrThrow()) {
                    assertEquals(input, filename)
                    assertEquals(TIMESTAMP, timestamp)
                    assertEquals(UUID, uuid)
                    assertEquals(PROCESS_ID, processIdentifier)
                    assertEquals(type, envelopeType)
                    assertEquals(payloadComplete, complete)
                    assertEquals(PayloadType.NATIVE_CRASH, payloadType)
                    assertEquals(USER_SESSION_ID, userSessionId)
                    assertEquals(SESSION_PART_ID, sessionPartId)
                }
            }
        }
    }

    @Test
    fun `from valid v2 filename with none decodes to empty session ids`() {
        val input = "p3_${TIMESTAMP}_${UUID}_${PROCESS_ID}_true_session_none_none_v2.json"
        with(StoredTelemetryMetadata.fromFilename(input).getOrThrow()) {
            assertEquals("", userSessionId)
            assertEquals("", sessionPartId)
            assertEquals(SupportedEnvelopeType.SESSION, envelopeType)
            assertEquals(PayloadType.SESSION, payloadType)
        }
    }

    @Test
    fun `v2 round trip preserves all fields`() {
        val original = StoredTelemetryMetadata(
            timestamp = TIMESTAMP,
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = SupportedEnvelopeType.SESSION,
            complete = false,
            payloadType = PayloadType.SESSION,
            userSessionId = USER_SESSION_ID,
            sessionPartId = SESSION_PART_ID,
        )
        val decoded = StoredTelemetryMetadata.fromFilename(original.filename).getOrThrow()
        assertEquals(original.timestamp, decoded.timestamp)
        assertEquals(original.uuid, decoded.uuid)
        assertEquals(original.processIdentifier, decoded.processIdentifier)
        assertEquals(original.envelopeType, decoded.envelopeType)
        assertEquals(original.complete, decoded.complete)
        assertEquals(original.payloadType, decoded.payloadType)
        assertEquals(original.userSessionId, decoded.userSessionId)
        assertEquals(original.sessionPartId, decoded.sessionPartId)
        assertEquals(original.filename, decoded.filename)
    }

    @Test
    fun `v2 round trip with empty ids preserves empty after decode`() {
        val original = StoredTelemetryMetadata(
            timestamp = TIMESTAMP,
            uuid = UUID,
            processIdentifier = PROCESS_ID,
            envelopeType = SupportedEnvelopeType.CRASH,
            payloadType = PayloadType.NATIVE_CRASH,
        )
        val decoded = StoredTelemetryMetadata.fromFilename(original.filename).getOrThrow()
        assertEquals("", decoded.userSessionId)
        assertEquals("", decoded.sessionPartId)
        assertEquals(original.filename, decoded.filename)
    }
}
