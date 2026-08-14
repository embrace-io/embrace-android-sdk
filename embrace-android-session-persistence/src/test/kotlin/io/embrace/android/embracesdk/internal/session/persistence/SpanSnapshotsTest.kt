package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class SpanSnapshotsTest {

    @Test
    fun `empty shard round-trips`() {
        val shard = SpanSnapshots()
        val decoded = SpanSnapshots.ADAPTER.decode(SpanSnapshots.ADAPTER.encode(shard))
        assertEquals(emptyList<SpanProto>(), decoded.spans)
    }

    @Test
    fun `multi span shard round-trips in order`() {
        val shard = SpanSnapshots(
            spans = listOf(
                SpanProto(span_id = "aaaaaaaaaaaaaaa1", name = "first"),
                SpanProto(span_id = "aaaaaaaaaaaaaaa2", name = "second"),
                SpanProto(span_id = "aaaaaaaaaaaaaaa3", name = "third"),
            ),
        )

        val decoded = SpanSnapshots.ADAPTER.decode(SpanSnapshots.ADAPTER.encode(shard))
        assertEquals(shard, decoded)
        assertEquals(listOf("first", "second", "third"), decoded.spans.map { it.name })
    }

    @Test
    fun `in-flight spans keep their null end time inside a shard`() {
        val shard = SpanSnapshots(
            spans = listOf(SpanProto(span_id = "aaaaaaaaaaaaaaa1", end_time_unix_nano = null)),
        )

        val decoded = SpanSnapshots.ADAPTER.decode(SpanSnapshots.ADAPTER.encode(shard))
        assertNull(decoded.spans.single().end_time_unix_nano)
    }
}
