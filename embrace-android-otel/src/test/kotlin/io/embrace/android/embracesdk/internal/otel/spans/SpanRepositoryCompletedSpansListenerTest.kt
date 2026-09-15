package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.internal.otel.sdk.StoreDataResult
import io.embrace.android.embracesdk.internal.payload.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SpanRepositoryCompletedSpansListenerTest {

    private lateinit var repository: SpanRepository
    private val observed = mutableListOf<List<Span>>()

    @Before
    fun setup() {
        repository = SpanRepository()
        observed.clear()
    }

    @Test
    fun `the listener is notified with the batch that was stored`() {
        repository.addCompletedOtelSpansListener(observed::add)
        assertEquals(StoreDataResult.SUCCESS, repository.storeCompletedOtelSpans(listOf(span("a"), span("b"))))
        assertEquals(listOf(listOf("a", "b")), observed.map { batch -> batch.map(Span::name) })
    }

    @Test
    fun `each batch is notified separately and in the order it was stored`() {
        repository.addCompletedOtelSpansListener(observed::add)
        repository.storeCompletedOtelSpans(listOf(span("a"), span("b")))
        repository.storeCompletedOtelSpans(listOf(span("c")))
        assertEquals(listOf(listOf("a", "b"), listOf("c")), observed.map { batch -> batch.map(Span::name) })
    }

    @Test
    fun `storing an empty batch does not notify`() {
        repository.addCompletedOtelSpansListener(observed::add)
        assertEquals(StoreDataResult.SUCCESS, repository.storeCompletedOtelSpans(emptyList()))
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `spans stored before registration are notified as one batch on registration`() {
        repository.storeCompletedOtelSpans(listOf(span("a")))
        repository.storeCompletedOtelSpans(listOf(span("b")))
        repository.addCompletedOtelSpansListener(observed::add)
        assertEquals(listOf(listOf("a", "b")), observed.map { batch -> batch.map(Span::name) })
    }

    @Test
    fun `registering with nothing stored does not notify`() {
        repository.addCompletedOtelSpansListener(observed::add)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `spans flushed before registration are not replayed`() {
        repository.storeCompletedOtelSpans(listOf(span("a")))
        repository.flushOtelSpans()
        repository.storeCompletedOtelSpans(listOf(span("b")))
        repository.addCompletedOtelSpansListener(observed::add)
        assertEquals(listOf(listOf("b")), observed.map { batch -> batch.map(Span::name) })
    }

    @Test
    fun `only the listener being registered is replayed to`() {
        repository.addCompletedOtelSpansListener(observed::add)
        repository.storeCompletedOtelSpans(listOf(span("a")))

        val other = mutableListOf<List<Span>>()
        repository.addCompletedOtelSpansListener(other::add)

        assertEquals(listOf(listOf("a")), observed.map { batch -> batch.map(Span::name) })
        assertEquals(listOf(listOf("a")), other.map { batch -> batch.map(Span::name) })
    }

    @Test
    fun `a listener that throws on replay does not fail registration`() {
        repository.storeCompletedOtelSpans(listOf(span("a")))
        repository.addCompletedOtelSpansListener { error("listener failed") }
        repository.addCompletedOtelSpansListener(observed::add)
        assertEquals(listOf(listOf("a")), observed.map { batch -> batch.map(Span::name) })
    }

    @Test
    fun `every registered listener is notified`() {
        val other = mutableListOf<List<Span>>()
        repository.addCompletedOtelSpansListener(observed::add)
        repository.addCompletedOtelSpansListener(other::add)
        repository.storeCompletedOtelSpans(listOf(span("a")))

        assertEquals(listOf("a"), observed.single().map(Span::name))
        assertEquals(listOf("a"), other.single().map(Span::name))
    }

    @Test
    fun `a throwing listener neither stops the others nor fails the store`() {
        repository.addCompletedOtelSpansListener { error("listener failed") }
        repository.addCompletedOtelSpansListener(observed::add)
        assertEquals(StoreDataResult.SUCCESS, repository.storeCompletedOtelSpans(listOf(span("a"))))

        assertEquals(listOf("a"), observed.single().map(Span::name))
        assertEquals(listOf("a"), repository.completedOtelSpans().map(Span::name))
    }

    @Test
    fun `a notified span is already readable from the repository`() {
        val readBack = mutableListOf<String?>()
        repository.addCompletedOtelSpansListener {
            readBack += repository.completedOtelSpans().map(Span::name)
        }
        repository.storeCompletedOtelSpans(listOf(span("a")))
        assertEquals(listOf("a"), readBack)
    }

    @Test
    fun `flushing spans does not notify`() {
        repository.addCompletedOtelSpansListener(observed::add)
        repository.storeCompletedOtelSpans(listOf(span("a")))
        assertEquals(listOf("a"), repository.flushOtelSpans().map(Span::name))
        assertEquals(1, observed.size)
    }

    @Test
    fun `spans stored after a flush notify only the new batch`() {
        repository.addCompletedOtelSpansListener(observed::add)
        repository.storeCompletedOtelSpans(listOf(span("a")))
        repository.flushOtelSpans()
        repository.storeCompletedOtelSpans(listOf(span("b")))
        assertEquals(listOf(listOf("a"), listOf("b")), observed.map { batch -> batch.map(Span::name) })
    }

    private fun span(name: String) = Span(name = name)
}
