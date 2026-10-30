package io.embrace.android.embracesdk.internal.network.parent

import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeSpanContext
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.semconv.EmbNetworkSpanParentAttributes
import io.opentelemetry.kotlin.tracing.SpanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class NetworkSpanParentServiceImplTest {

    private lateinit var spanService: FakeSpanService
    private lateinit var errorHandler: FakeErrorHandler
    private lateinit var telemetryService: FakeInternalTelemetryService
    private lateinit var service: NetworkSpanParentServiceImpl
    private var userSessionId = "session-1"

    @Before
    fun setUp() {
        spanService = FakeSpanService()
        errorHandler = FakeErrorHandler()
        telemetryService = FakeInternalTelemetryService()
        service = NetworkSpanParentServiceImpl(spanService, errorHandler, telemetryService) { userSessionId }
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

    @Test
    fun `replacing an active parent warns with the name of a tracked span and the span ID of an untracked one`() {
        val checkout = trackedSpan("checkout")
        val untracked = untrackedContext()
        service.set(checkout.spanContext)
        service.set(untracked)

        assertEquals(
            "Network span parent 'checkout' replaced by '${untracked.spanId}' while still active",
            errorHandler.warningMessages.single().msg,
        )
    }

    @Test
    fun `replacing a cleared parent is not a conflict`() {
        service.clear(service.set(untrackedContext()))
        service.set(untrackedContext())

        assertTrue(errorHandler.warningMessages.isEmpty())
        assertTrue(telemetryService.appliedLimits.isEmpty())
    }

    @Test
    fun `a replaced tracked span gets one event naming the replacing span for its first conflict only`() {
        val checkout = trackedSpan("checkout")
        val search = trackedSpan("search")
        service.set(checkout.spanContext)
        service.set(search.spanContext)
        service.set(checkout.spanContext)
        service.set(trackedSpan("results").spanContext)

        with(checkout.events.single()) {
            assertEquals("emb-network-span-parent-replaced", name)
            assertEquals(mapOf(EmbNetworkSpanParentAttributes.EMB_NETWORK_SPAN_PARENT_REPLACED_BY to "search"), attributes)
        }
        assertEquals("checkout", search.events.single().attributes[EmbNetworkSpanParentAttributes.EMB_NETWORK_SPAN_PARENT_REPLACED_BY])
        assertEquals(3, errorHandler.warningMessages.size)
    }

    @Test
    fun `an ended replaced span gets no event`() {
        val checkout = trackedSpan("checkout")
        service.set(checkout.spanContext)
        checkout.stop()
        service.set(untrackedContext())

        assertTrue(checkout.events.isEmpty())
        assertEquals(1, errorHandler.warningMessages.size)
    }

    @Test
    fun `a repeated conflict is counted rather than reported until the user session ends`() {
        val first = untrackedContext()
        val second = untrackedContext()
        service.set(first)
        service.set(second)
        service.set(first)
        service.set(second)

        assertEquals(2, errorHandler.warningMessages.size)
        assertEquals(listOf("network_span_parent_conflict" to AppliedLimitType.DROP), telemetryService.appliedLimits)

        service.onUserSessionEnded(userSessionId)
        service.set(first)
        service.set(second)
        assertEquals(3, errorHandler.warningMessages.size)
    }

    @Test
    fun `at most 10 conflicts are reported per user session`() {
        repeat(12) { service.set(untrackedContext()) }

        assertEquals(10, errorHandler.warningMessages.size)
        assertEquals(listOf("network_span_parent_conflict" to AppliedLimitType.DROP), telemetryService.appliedLimits)

        service.onUserSessionEnded(userSessionId)
        service.set(untrackedContext())
        service.set(untrackedContext())
        assertEquals(11, errorHandler.warningMessages.size)
    }

    private fun currentParent(): SpanContext? = service.currentParent()

    private fun untrackedContext(): SpanContext = checkNotNull(FakeEmbraceSdkSpan.started().spanContext)

    private fun trackedSpan(): FakeEmbraceSdkSpan = FakeEmbraceSdkSpan.started().also(spanService::addKnownSpan)

    private fun trackedSpan(name: String): FakeEmbraceSdkSpan =
        FakeEmbraceSdkSpan(name = name).apply { start() }.also(spanService::addKnownSpan)
}
