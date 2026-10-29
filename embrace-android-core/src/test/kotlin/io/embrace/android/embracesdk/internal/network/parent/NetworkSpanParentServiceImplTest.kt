package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeSpanContext
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.opentelemetry.kotlin.tracing.SpanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

internal class NetworkSpanParentServiceImplTest {

    private lateinit var spanService: FakeSpanService
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var service: NetworkSpanParentServiceImpl
    private var userSessionId = "session-1"

    @Before
    fun setUp() {
        spanService = FakeSpanService()
        errorHandler = FakeErrorHandler()
        service = NetworkSpanParentServiceImpl(spanService, errorHandler) { userSessionId }
    }

    @Test
    fun `setting replaces the parent and only the most recent token clears it`() {
        val first = untrackedContext()
        val second = untrackedContext()
        val firstToken = service.set(first)
        assertSame(first, currentParent())

        val secondToken = service.set(second)
        assertNotSame(firstToken, secondToken)
        assertSame(second, currentParent())

        service.clear(firstToken)
        assertSame(second, currentParent())
        service.clear(secondToken)
        assertNull(currentParent())
    }

    @Test
    fun `a tracked parent is cleared when its span stops but not when another span does`() {
        val span = trackedSpan()
        service.set(span.spanContext)

        service.onSpanStopped(checkNotNull(trackedSpan().spanId))
        assertSame(span.spanContext, currentParent())
        service.onSpanStopped(checkNotNull(span.spanId))
        assertNull(currentParent())
    }

    @Test
    fun `an untracked parent is not cleared by a span stopping`() {
        val context = untrackedContext()
        service.set(context)
        service.onSpanStopped(context.spanId)
        assertSame(context, currentParent())
    }

    @Test
    fun `the parent is cleared when the user session it was set in ends`() {
        service.set(untrackedContext())
        service.onUserSessionEnded("session-2")
        assertNotNull(currentParent())
        service.onUserSessionEnded("session-1")
        assertNull(currentParent())
    }

    @Test
    fun `a parent set with no active user session is cleared when the next one ends`() {
        userSessionId = ""
        service.set(untrackedContext())
        service.onUserSessionEnded("session-2")
        assertNull(currentParent())
    }

    @Test
    fun `rejected parents keep the previous parent and warn once`() {
        val context = untrackedContext()
        service.set(context)
        val ended = trackedSpan().apply { stop() }

        assertSame(NoopNetworkSpanParentToken, service.set(FakeSpanContext()))
        assertSame(NoopNetworkSpanParentToken, service.set(null))
        assertSame(NoopNetworkSpanParentToken, service.set(ended.spanContext))
        assertSame(context, currentParent())
        assertEquals(1, errorHandler.warningMessages.size)
    }

    private fun currentParent(): SpanContext? = service.currentParent()

    private fun untrackedContext(): SpanContext = checkNotNull(FakeEmbraceSdkSpan.started().spanContext)

    private fun trackedSpan(): FakeEmbraceSdkSpan = FakeEmbraceSdkSpan.started().also(spanService::addKnownSpan)
}
