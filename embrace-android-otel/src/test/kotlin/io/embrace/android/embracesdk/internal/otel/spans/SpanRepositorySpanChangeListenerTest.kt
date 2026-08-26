package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class SpanRepositorySpanChangeListenerTest {

    private lateinit var repository: SpanRepository
    private val observed = mutableListOf<EmbraceSdkSpan>()

    @Before
    fun setup() {
        repository = SpanRepository()
        observed.clear()
    }

    @Test
    fun `the listener is notified with the span that changed`() {
        val span = FakeEmbraceSdkSpan.started()
        repository.addSpanChangeListener(observed::add)
        repository.notifySpanChanged(span)
        assertEquals(listOf(span), observed)
    }

    @Test
    fun `each change is notified separately and in the order it happened`() {
        val first = FakeEmbraceSdkSpan.started()
        val second = FakeEmbraceSdkSpan.stopped()
        repository.addSpanChangeListener(observed::add)
        repository.notifySpanChanged(first)
        repository.notifySpanChanged(second)
        repository.notifySpanChanged(first)
        assertEquals(listOf(first, second, first), observed)
    }

    @Test
    fun `changes made before registration are not replayed`() {
        repository.notifySpanChanged(FakeEmbraceSdkSpan.started())
        repository.addSpanChangeListener(observed::add)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `every registered listener is notified`() {
        val other = mutableListOf<EmbraceSdkSpan>()
        val span = FakeEmbraceSdkSpan.started()
        repository.addSpanChangeListener(observed::add)
        repository.addSpanChangeListener(other::add)
        repository.notifySpanChanged(span)
        assertEquals(listOf(span), observed)
        assertEquals(listOf(span), other)
    }

    @Test
    fun `a throwing listener neither stops the others nor fails the change`() {
        val span = FakeEmbraceSdkSpan.started()
        repository.addSpanChangeListener { error("listener failed") }
        repository.addSpanChangeListener(observed::add)
        repository.notifySpanChanged(span)
        assertEquals(listOf(span), observed)
    }

    @Test
    fun `notifying with no listeners registered does not throw`() {
        repository.notifySpanChanged(FakeEmbraceSdkSpan.started())
    }
}
