package io.embrace.android.embracesdk.internal.session.persistence

import org.junit.Assert.assertEquals
import org.junit.Test

internal class SpanRecordsTest {

    @Test
    fun `a write time of zero costs nothing because it is never encoded`() {
        assertEquals(0L, writtenAtSize(0L))
    }

    @Test
    fun `a write time costs its tag and the varint holding it`() {
        assertEquals(2L, writtenAtSize(127L))
        assertEquals(3L, writtenAtSize(128L))
        assertEquals(7L, writtenAtSize(1692201601000L))
        assertEquals(10L, writtenAtSize(Long.MAX_VALUE))
    }

    @Test
    fun `a write time costs exactly what it adds to an encoded collection`() {
        val spans = SpanCollection(spans = listOf(fullyPopulatedSpanProto))
        val writtenAtMs = 1692201601000L
        assertEquals(
            SpanCollection.ADAPTER.encode(spans.copy(written_at_ms = writtenAtMs)).size.toLong(),
            SpanCollection.ADAPTER.encode(spans).size + writtenAtSize(writtenAtMs),
        )
    }
}
