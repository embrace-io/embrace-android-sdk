package io.embrace.android.embracesdk.internal.session.persistence

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

internal class CompletedSpansReaderTest {

    private companion object {
        /** Field 2 as a varint, which no version of the log has ever held. */
        private val UNKNOWN_FIELD = byteArrayOf(0x10, 0x01)

        /** Field 1 tagged with wire type 6, which is not a field encoding protobuf defines. */
        private val INVALID_FIELD_ENCODING = byteArrayOf(0x0E)

        /** Field 1, length delimited, claiming more bytes than any log could hold. */
        private val OVERSIZED_LENGTH_PREFIX = byteArrayOf(0x0A, -1, -1, -1, -1, 0x07)

        private fun span(id: String) = fullyPopulatedSpanProto.copy(span_id = id)

        private fun read(bytes: ByteArray): List<SpanProto> = Buffer().write(bytes).use(::readCompletedSpans)
    }

    private val first = span("aaaaaaaaaaaaaaa1")
    private val second = span("aaaaaaaaaaaaaaa2")
    private val third = span("aaaaaaaaaaaaaaa3")

    @Test
    fun `an empty log reads back no spans`() {
        assertEquals(emptyList<SpanProto>(), read(byteArrayOf()))
    }

    @Test
    fun `every field of a logged span survives the log`() {
        assertEquals(listOf(fullyPopulatedSpanProto), read(completedSpansLog(listOf(fullyPopulatedSpanProto))))
    }

    @Test
    fun `a span with no populated fields reads back`() {
        assertEquals(listOf(SpanProto()), read(completedSpansLog(listOf(SpanProto()))))
    }

    @Test
    fun `records read back in the order they were appended`() {
        assertEquals(listOf(first, second, third), read(completedSpansLog(listOf(first, second, third))))
    }

    @Test
    fun `a record holding several spans reads all of them back`() {
        val batched = CompletedSpans.ADAPTER.encode(CompletedSpans(spans = listOf(first, second)))
        assertEquals(listOf(first, second), read(batched))
    }

    @Test
    fun `a torn final record is dropped and the records before it are kept`() {
        val log = completedSpansLog(listOf(first, second))
        assertEquals(listOf(first), read(log.copyOf(log.size - 1)))
    }

    @Test
    fun `every truncation of a log reads back one of its prefixes`() {
        val spans = listOf(first, second, third)
        val log = completedSpansLog(spans)

        (0..log.size).forEach { length ->
            val recovered = read(log.copyOf(length))
            assertEquals("truncated to $length bytes", spans.take(recovered.size), recovered)
        }
    }

    @Test
    fun `an oversized length prefix does not read past the end of the log`() {
        val log = completedSpansLog(listOf(first)) + OVERSIZED_LENGTH_PREFIX
        assertEquals(listOf(first), read(log))
    }

    @Test
    fun `a malformed frame throws even with no records behind it`() {
        try {
            read(INVALID_FIELD_ENCODING)
            fail("expected a malformed frame to throw")
        } catch (expected: IOException) {
            // a short frame is a torn append, but a malformed one is corruption at any offset
        }
    }

    @Test
    fun `a field a later SDK added is skipped`() {
        val log = completedSpansLog(listOf(first)) + UNKNOWN_FIELD + completedSpansLog(listOf(second))
        assertEquals(listOf(first, second), read(log))
    }

    @Test
    fun `a malformed frame behind an intact record throws so the caller can report it`() {
        val log = completedSpansLog(listOf(first)) + INVALID_FIELD_ENCODING + completedSpansLog(listOf(second))

        try {
            read(log)
            fail("expected corruption with records behind it to throw")
        } catch (expected: IOException) {
            // the records before it are still lost, but reporting beats delivering a partial log
        }
    }
}
