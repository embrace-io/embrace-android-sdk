package io.embrace.android.embracesdk.internal.session.persistence

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        /** An intact frame round a record body holding an invalid field encoding. */
        private val UNDECODABLE_RECORD = byteArrayOf(0x0A, 0x01, 0x0E)

        private fun span(id: String) = fullyPopulatedSpanProto.copy(span_id = id)

        private fun decode(bytes: ByteArray): DecodedSpans = Buffer().write(bytes).use(::readCompletedSpans)

        private fun read(bytes: ByteArray): List<SpanProto> = decode(bytes).spans

        private fun read(bytes: ByteArray, maxBytes: Long): List<SpanProto> =
            Buffer().write(bytes).use { readCompletedSpans(it, maxBytes) }.spans

        private fun readBoundedRecords(bytes: ByteArray, maxRecordBytes: Long): List<SpanProto> =
            Buffer().write(bytes).use { readCompletedSpans(it, MAX_PART_FILE_BYTES, maxRecordBytes) }.spans

        private fun readBoundedSpans(bytes: ByteArray, maxSpans: Int): DecodedSpans =
            Buffer().write(bytes).use {
                readCompletedSpans(it, MAX_PART_FILE_BYTES, MAX_RECORD_BYTES, maxSpans)
            }

        /** The length a record declares, which is the encoded span the frame wraps. */
        private fun declaredLengthOf(span: SpanProto): Long = SpanProto.ADAPTER.encode(span).size.toLong()

        /** The budget a record consumes, which is the record itself and not the framing round it. */
        private fun budgetOf(vararg spans: SpanProto): Long =
            spans.sumOf { SpanProto.ADAPTER.encode(it).size }.toLong()
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
    fun `a record declaring more than the record bound is dropped along with the rest of the log`() {
        val small = paddedSpanProto(paddedSpanId(1), padding = 8)
        val large = paddedSpanProto(paddedSpanId(2), padding = 4096)
        val log = completedSpansLog(listOf(small, large, small))
        assertEquals(listOf(small), readBoundedRecords(log, declaredLengthOf(large) - 1))
    }

    @Test
    fun `a record exactly at the record bound reads back`() {
        val log = completedSpansLog(listOf(first, second))
        assertEquals(listOf(first, second), readBoundedRecords(log, declaredLengthOf(first)))
    }

    @Test
    fun `a first record declaring more than the record bound reads back no spans`() {
        val log = completedSpansLog(listOf(first, second))
        assertEquals(emptyList<SpanProto>(), readBoundedRecords(log, declaredLengthOf(first) - 1))
    }

    @Test
    fun `a length prefix beyond the record bound is rejected before the body is read`() {
        val log = completedSpansLog(listOf(first)) + OVERSIZED_LENGTH_PREFIX + completedSpansLog(listOf(second))
        assertEquals(listOf(first), readBoundedRecords(log, MAX_RECORD_BYTES))
    }

    @Test
    fun `a large but legitimate record reads back under the production bound`() {
        val padded = paddedSpanProto(paddedSpanId(1), padding = 64 * 1024)
        assertEquals(listOf(padded), readBoundedRecords(completedSpansLog(listOf(padded)), MAX_RECORD_BYTES))
    }

    @Test
    fun `records past the span limit are dropped`() {
        val log = completedSpansLog(listOf(first, second, third))
        assertEquals(listOf(first, second), readBoundedSpans(log, maxSpans = 2).spans)
    }

    @Test
    fun `a log at the span limit reads back in full and reports nothing`() {
        val decoded = readBoundedSpans(completedSpansLog(listOf(first, second)), maxSpans = 2)
        assertEquals(listOf(first, second), decoded.spans)
        assertFalse(decoded.spanLimitReached)
    }

    @Test
    fun `dropping records past the span limit is reported`() {
        val log = completedSpansLog(listOf(first, second, third))
        assertTrue(readBoundedSpans(log, maxSpans = 2).spanLimitReached)
    }

    @Test
    fun `a span limit of zero reads back no spans`() {
        val decoded = readBoundedSpans(completedSpansLog(listOf(first)), maxSpans = 0)
        assertEquals(emptyList<SpanProto>(), decoded.spans)
        assertTrue(decoded.spanLimitReached)
    }

    @Test
    fun `a record past the span limit is not decoded at all`() {
        val log = completedSpansLog(listOf(first, second)) + UNDECODABLE_RECORD
        val decoded = readBoundedSpans(log, maxSpans = 2)
        assertEquals(listOf(first, second), decoded.spans)
        assertNull(decoded.corruption)
        assertTrue(decoded.spanLimitReached)
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

    @Test
    fun `an undecodable record is dropped and the records either side of it are kept`() {
        val log = completedSpansLog(listOf(first)) + UNDECODABLE_RECORD + completedSpansLog(listOf(second))
        assertEquals(listOf(first, second), read(log))
    }

    @Test
    fun `an undecodable record is reported so the caller can track it`() {
        val log = completedSpansLog(listOf(first)) + UNDECODABLE_RECORD
        assertNotNull(decode(log).corruption)
    }

    @Test
    fun `a log every record of which is undecodable reads back no spans`() {
        val decoded = decode(UNDECODABLE_RECORD + UNDECODABLE_RECORD)
        assertEquals(emptyList<SpanProto>(), decoded.spans)
        assertNotNull(decoded.corruption)
    }

    @Test
    fun `a log with no undecodable records reports no corruption`() {
        assertNull(decode(completedSpansLog(listOf(first, second))).corruption)
    }

    @Test
    fun `an undecodable record counts against the budget`() {
        val log = completedSpansLog(listOf(first)) + UNDECODABLE_RECORD + completedSpansLog(listOf(second))
        assertEquals(listOf(first), read(log, budgetOf(first, second)))
    }

    @Test
    fun `records past the budget are dropped`() {
        val log = completedSpansLog(listOf(first, second, third))
        assertEquals(listOf(first, second), read(log, budgetOf(first, second)))
    }

    @Test
    fun `a budget landing inside a record drops that record whole`() {
        val log = completedSpansLog(listOf(first, second))
        assertEquals(listOf(first), read(log, budgetOf(first, second) - 1))
    }

    @Test
    fun `a first record larger than the budget reads back no spans`() {
        val log = completedSpansLog(listOf(first, second))
        assertEquals(emptyList<SpanProto>(), read(log, budgetOf(first) - 1))
    }

    @Test
    fun `a log exactly filling the budget reads back in full`() {
        val log = completedSpansLog(listOf(first, second))
        assertEquals(listOf(first, second), read(log, budgetOf(first, second)))
    }

    @Test
    fun `a field a later SDK added does not count against the budget`() {
        val log = completedSpansLog(listOf(first)) + UNKNOWN_FIELD + completedSpansLog(listOf(second))
        assertEquals(listOf(first, second), read(log, budgetOf(first, second)))
    }

    @Test
    fun `the drain releases every decoded span`() {
        val decoded = decode(completedSpansLog(listOf(first, second)))
        assertEquals(listOf(first.span_id, second.span_id), decoded.drainToPayload().map { it.spanId })
        assertEquals(emptyList<SpanProto>(), decoded.spans)
    }

    @Test
    fun `a span added during the drain is left behind by it`() {
        val decoded = DecodedSpans(AppendingOnRemoval(listOf(first, second), third), corruption = null)
        assertEquals(listOf(first.span_id, second.span_id), decoded.drainToPayload().map { it.spanId })
        assertEquals(listOf(third), decoded.spans)
    }

    private class AppendingOnRemoval(
        spans: List<SpanProto>,
        private val added: SpanProto,
    ) : ArrayList<SpanProto>(spans) {

        private var appended = false

        override fun removeAt(index: Int): SpanProto = super.removeAt(index).also {
            if (!appended) {
                appended = true
                add(added)
            }
        }
    }
}
