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

/**
 * Covers what the snapshots reader adds over the record framing it shares with the completed spans
 * reader, which [CompletedSpansReaderTest] covers.
 */
internal class SpanSnapshotsReaderTest {

    private companion object {
        /** Field 3 as a varint, which no version of the file has ever held. */
        private val UNKNOWN_FIELD = byteArrayOf(0x18, 0x01)

        /** Field 1 tagged with wire type 6, which is not a field encoding protobuf defines. */
        private val INVALID_FIELD_ENCODING = byteArrayOf(0x0E)

        /** An intact frame round a record body holding an invalid field encoding. */
        private val UNDECODABLE_RECORD = byteArrayOf(0x12, 0x01, 0x0E)

        private fun span(id: String) = fullyPopulatedSpanProto.copy(span_id = id)

        private fun decode(bytes: ByteArray): DecodedSpans = Buffer().write(bytes).use(::readSpanSnapshots)

        private fun decode(bytes: ByteArray, maxBytes: Long): DecodedSpans =
            Buffer().write(bytes).use { readSpanSnapshots(it, maxBytes) }

        private fun decodeBoundedRecords(bytes: ByteArray, maxRecordBytes: Long): DecodedSpans =
            Buffer().write(bytes).use { readSpanSnapshots(it, MAX_PART_FILE_BYTES, maxRecordBytes) }

        private fun decodeBoundedSpans(bytes: ByteArray, maxSpans: Int): DecodedSpans =
            Buffer().write(bytes).use {
                readSpanSnapshots(it, MAX_PART_FILE_BYTES, MAX_RECORD_BYTES, maxSpans)
            }

        private fun read(bytes: ByteArray): List<SpanProto> = decode(bytes).spans

        private fun read(bytes: ByteArray, maxBytes: Long): List<SpanProto> = decode(bytes, maxBytes).spans

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
    fun `a file holding only a rollup record reads back no spans`() {
        val decoded = decode(spanSnapshotsRollup(emptyList()))
        assertEquals(emptyList<SpanProto>(), decoded.spans)
        assertFalse(decoded.spanLimitReached)
        assertNull(decoded.corruption)
    }

    @Test
    fun `an empty file is rejected`() {
        assertRejected(byteArrayOf())
    }

    @Test
    fun `a file holding no rollup record at all is rejected`() {
        assertRejected(spanSnapshotsAppend(listOf(first)))
    }

    @Test
    fun `a file rolled up under another format version is rejected`() {
        assertRejected(SpanSnapshots.ADAPTER.encode(SpanSnapshots(format_version = FORMAT_VERSION + 1)))
    }

    @Test
    fun `a rollup discards every record written before it`() {
        val file = spanSnapshotsRollup(listOf(first)) +
            spanSnapshotsAppend(listOf(second)) +
            spanSnapshotsRollup(listOf(third))
        assertEquals(listOf(third), read(file))
    }

    @Test
    fun `an append holding several spans reads all of them back`() {
        val file = spanSnapshotsRollup(emptyList()) + spanSnapshotsAppend(listOf(first, second))
        assertEquals(listOf(first, second), read(file))
    }

    @Test
    fun `spans read back in the order they were first written`() {
        val file = spanSnapshotsRollup(listOf(first)) +
            spanSnapshotsAppend(listOf(second)) +
            spanSnapshotsAppend(listOf(third))
        assertEquals(listOf(first, second, third), read(file))
    }

    @Test
    fun `a later record for a span supersedes the earlier one`() {
        val superseding = first.copy(name = "superseded")
        val file = spanSnapshotsRollup(listOf(first)) + spanSnapshotsAppend(listOf(superseding))
        assertEquals(listOf(superseding), read(file))
    }

    @Test
    fun `a superseded span keeps the place it was first written in`() {
        val superseding = first.copy(name = "superseded")
        val file = spanSnapshotsRollup(listOf(first, second)) + spanSnapshotsAppend(listOf(superseding))
        assertEquals(listOf(superseding, second), read(file))
    }

    @Test
    fun `records for spans with no populated fields collapse into one`() {
        val file = spanSnapshotsRollup(emptyList()) + spanSnapshotsAppend(listOf(SpanProto(), SpanProto()))
        assertEquals(listOf(SpanProto()), read(file))
    }

    @Test
    fun `every truncation past the rollup record reads back one of its prefixes`() {
        val spans = listOf(first, second, third)
        val file = spanSnapshotsRollup(spans)
        val rollupBytes = SpanSnapshots.ADAPTER.encode(SpanSnapshots(format_version = FORMAT_VERSION)).size

        (rollupBytes..file.size).forEach { length ->
            val recovered = read(file.copyOf(length))
            assertEquals("truncated to $length bytes", spans.take(recovered.size), recovered)
        }
    }

    @Test
    fun `a record declaring more than the record bound is dropped along with the rest of the file`() {
        val small = paddedSpanProto(paddedSpanId(1), padding = 8)
        val large = paddedSpanProto(paddedSpanId(2), padding = 4096)
        val other = paddedSpanProto(paddedSpanId(3), padding = 8)
        val decoded = decodeBoundedRecords(
            spanSnapshotsRollup(listOf(small, large, other)),
            declaredLengthOf(large) - 1,
        )
        assertEquals(listOf(small), decoded.spans)
    }

    @Test
    fun `records past the budget are dropped`() {
        val spans = read(spanSnapshotsRollup(listOf(first, second, third)), budgetOf(first, second))
        assertEquals(listOf(first, second), spans)
    }

    @Test
    fun `a rollup record does not count against the budget`() {
        val file = spanSnapshotsRollup(listOf(first, second))
        assertEquals(listOf(first, second), read(file, budgetOf(first, second)))
    }

    @Test
    fun `records for spans past the span limit are dropped`() {
        val decoded = decodeBoundedSpans(spanSnapshotsRollup(listOf(first, second, third)), maxSpans = 2)
        assertEquals(listOf(first, second), decoded.spans)
        assertTrue(decoded.spanLimitReached)
    }

    @Test
    fun `a record superseding a span already held reads back at the span limit`() {
        val superseding = first.copy(name = "superseded")
        val file = spanSnapshotsRollup(listOf(first, second)) + spanSnapshotsAppend(listOf(superseding))
        val decoded = decodeBoundedSpans(file, maxSpans = 2)
        assertEquals(listOf(superseding, second), decoded.spans)
        assertFalse(decoded.spanLimitReached)
    }

    @Test
    fun `an undecodable record at the span limit is reported rather than dropped by it`() {
        val file = spanSnapshotsRollup(listOf(first, second)) + UNDECODABLE_RECORD
        val decoded = decodeBoundedSpans(file, maxSpans = 2)
        // a record is decoded before the span it supersedes is known
        assertEquals(listOf(first, second), decoded.spans)
        assertNotNull(decoded.corruption)
        assertFalse(decoded.spanLimitReached)
    }

    @Test
    fun `an undecodable record is reported and the records either side of it are kept`() {
        val file = spanSnapshotsRollup(listOf(first)) + UNDECODABLE_RECORD + spanSnapshotsAppend(listOf(second))
        val decoded = decode(file)
        assertEquals(listOf(first, second), decoded.spans)
        assertNotNull(decoded.corruption)
        assertFalse(decoded.spanLimitReached)
    }

    @Test
    fun `a malformed frame behind an intact record throws so the caller can report it`() {
        assertRejected(
            spanSnapshotsRollup(listOf(first)) + INVALID_FIELD_ENCODING + spanSnapshotsAppend(listOf(second)),
        )
    }

    @Test
    fun `a field a later SDK added is skipped`() {
        val file = spanSnapshotsRollup(listOf(first)) + UNKNOWN_FIELD + spanSnapshotsAppend(listOf(second))
        assertEquals(listOf(first, second), read(file))
    }

    private fun assertRejected(file: ByteArray) {
        try {
            read(file)
            fail("expected a file that cannot be read back to throw")
        } catch (expected: IOException) {
            // a file the reader cannot make sense of is reported rather than delivered in part
        }
    }
}
