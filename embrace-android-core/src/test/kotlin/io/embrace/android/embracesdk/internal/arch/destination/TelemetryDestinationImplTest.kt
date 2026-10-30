package io.embrace.android.embracesdk.internal.arch.destination

import io.embrace.android.embracesdk.Severity
import io.embrace.android.embracesdk.assertions.assertNonSystemStateValue
import io.embrace.android.embracesdk.assertions.assertSystemStateValue
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeClock.Companion.DEFAULT_FAKE_CURRENT_TIME
import io.embrace.android.embracesdk.fakes.FakeCurrentSessionPartSpan
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeErrorHandler
import io.embrace.android.embracesdk.fakes.FakeInternalTelemetryService
import io.embrace.android.embracesdk.fakes.FakeLogRecord
import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.TestState
import io.embrace.android.embracesdk.fakes.TestStateValue
import io.embrace.android.embracesdk.fakes.getTraceIdFromTraceparent
import io.embrace.android.embracesdk.internal.arch.attrs.asPair
import io.embrace.android.embracesdk.internal.arch.datasource.LogSeverity
import io.embrace.android.embracesdk.internal.arch.datasource.SpanEventImpl
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.ErrorCodeAttribute
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.Log
import io.embrace.android.embracesdk.internal.arch.schema.TelemetryAttributes
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.network.http.MutableHttpRequestInfoImpl
import io.embrace.android.embracesdk.internal.network.parent.NetworkSpanParentServiceImpl
import io.embrace.android.embracesdk.internal.otel.sdk.hasEmbraceAttribute
import io.embrace.android.embracesdk.internal.otel.toEmbracePayload
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.network.NetworkSpanParent
import io.embrace.android.embracesdk.network.NetworkSpanParentProvider
import io.embrace.android.embracesdk.semconv.EmbStateTransitionAttributes
import io.opentelemetry.kotlin.tracing.SpanContext
import io.opentelemetry.kotlin.tracing.StatusCode
import io.opentelemetry.kotlin.tracing.createSpanContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class TelemetryDestinationImplTest {
    private lateinit var impl: TelemetryDestination
    private lateinit var clock: FakeClock
    private lateinit var spanService: FakeSpanService
    private lateinit var logger: FakeOpenTelemetryLogger
    private lateinit var currentSessionPartSpan: FakeCurrentSessionPartSpan
    private var sessionDataUpdated = false
    private val askedParents = mutableListOf<SpanContext?>()
    private var networkSpanParent: NetworkSpanParent = NetworkSpanParent.USE_DEFAULT

    @Before
    fun setup() {
        clock = FakeClock()
        spanService = FakeSpanService()
        logger = FakeOpenTelemetryLogger()
        currentSessionPartSpan = FakeCurrentSessionPartSpan()
        impl = createDestination()
    }

    private fun createDestination(
        networkSpanParentProvider: NetworkSpanParentProvider = NetworkSpanParentProvider { _, currentParent ->
            askedParents.add(currentParent)
            networkSpanParent
        },
    ): TelemetryDestination =
        TelemetryDestinationImpl(
            clock = clock,
            spanService = spanService,
            loggerSupplier = { logger },
            currentSessionPartSpan = currentSessionPartSpan,
            networkSpanParentProvider = networkSpanParentProvider,
        ).also {
            it.sessionUpdateAction = { sessionDataUpdated = true }
        }

    @Test
    fun `check expected values added to every OTel log`() {
        impl = createDestination()
        val expectedSchemaType = Log(
            TelemetryAttributes(
                customAttributes = mapOf("foo" to "bar"),
            ),
        )
        impl.addLog(
            schemaType = expectedSchemaType,
            severity = LogSeverity.ERROR,
            message = "test",
        )
        logger.logs.single().assertFakeEvent(
            expectedTimestamp = clock.now().millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = false,
        )
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `typed log attributes are emitted as their payload string`() {
        impl = createDestination()
        impl.addLog(
            schemaType = Log(
                TelemetryAttributes(
                    customAttributes = mapOf("long" to 5L, "list" to listOf("a", "b"), "bytes" to byteArrayOf(1, 2)),
                ),
            ),
            severity = LogSeverity.INFO,
            message = "test",
        )
        val attributes = logger.logs.single().attributes
        assertEquals("5", attributes["long"])
        assertEquals("[a, b]", attributes["list"])
        assertEquals("[1, 2]", attributes["bytes"])
    }

    private fun FakeLogRecord.assertFakeEvent(
        expectedTimestamp: Long,
        expectedMessage: String?,
        expectedSeverity: Severity,
        expectedSchemaType: SchemaType,
        expectedIsPrivate: Boolean,
    ) {
        assertEquals(expectedTimestamp, timestamp)
        assertEquals(expectedMessage, body)
        assertEquals(expectedSeverity.name, severityNumber?.name)
        assertAttributes(
            expectedSchemaType = expectedSchemaType,
            expectedAdditionalAttributes = if (expectedIsPrivate) {
                mapOf(PrivateSpan.asPair())
            } else {
                emptyMap()
            },
        )
    }

    private fun FakeLogRecord.assertAttributes(
        expectedSchemaType: SchemaType,
        expectedAdditionalAttributes: Map<String, String>,
    ) {
        val logAttributes = attributes.mapValues { it.value.toString() }
        (expectedSchemaType.attributes() + expectedAdditionalAttributes).forEach { attr ->
            assertEquals(attr.value, logAttributes[attr.key])
        }
        assertTrue(logAttributes.hasEmbraceAttribute(expectedSchemaType.telemetryType))
    }

    @Test
    fun `check that private value is set`() {
        val expectedSchemaType = Log(TelemetryAttributes())
        impl.addLog(
            schemaType = expectedSchemaType,
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = true,
        )
        logger.logs.single().assertFakeEvent(
            expectedTimestamp = clock.now().millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = true,
        )

        impl.addLog(
            schemaType = expectedSchemaType,
            severity = LogSeverity.ERROR,
            message = "test",
            isPrivate = false,
        )
        logger.logs.last().assertFakeEvent(
            expectedTimestamp = clock.now().millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = false,
        )
    }

    @Test
    fun `timestamp can be overridden`() {
        val expectedSchemaType = Log(TelemetryAttributes())
        val fakeTimeMs = DEFAULT_FAKE_CURRENT_TIME
        impl.addLog(
            schemaType = Log(TelemetryAttributes()),
            severity = LogSeverity.ERROR,
            message = "test",
            timestampMs = fakeTimeMs,
        )

        logger.logs.single().assertFakeEvent(
            expectedTimestamp = fakeTimeMs.millisToNanos(),
            expectedMessage = "test",
            expectedSeverity = Severity.ERROR,
            expectedSchemaType = expectedSchemaType,
            expectedIsPrivate = false,
        )
    }

    @Test
    fun `state capture records the value type only for system values`() {
        val initialValue = TestStateValue.SystemValue("init")
        val nonSystemValue = TestStateValue.NonSystemValue("foo")
        val systemValue = TestStateValue.SystemValue("sys")
        val dupeNameValue = TestStateValue.NonSystemValue("sys")
        val token = impl.startSessionPartStateCapture(TestState(initialValue))
        val span = spanService.createdSpans.single()
        span.attributes.assertSystemStateValue(initialValue, EmbStateTransitionAttributes.EMB_STATE_INITIAL_VALUE)

        token.update(newValue = nonSystemValue, transitionTimeMs = clock.tick())
        token.update(newValue = dupeNameValue, transitionTimeMs = clock.tick())
        token.update(newValue = systemValue, transitionTimeMs = clock.tick())
        token.update(
            newValue = dupeNameValue,
            transitionTimeMs = clock.tick(),
            transitionAttributes = mapOf(EmbStateTransitionAttributes.EMB_STATE_VALUE_TYPE to "spoofed"),
        )

        val events = span.events.toList()
        assertEquals(4, events.size)
        events[0].attributes.assertNonSystemStateValue(nonSystemValue, EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)
        events[1].attributes.assertNonSystemStateValue(dupeNameValue, EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)
        events[2].attributes.assertSystemStateValue(systemValue, EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)
        events[3].attributes.assertNonSystemStateValue(dupeNameValue, EmbStateTransitionAttributes.EMB_STATE_NEW_VALUE)
    }

    @Test
    fun `test start span capture`() {
        val span = impl.startSpanCapture(SchemaType.Breadcrumb("Whoops"), 5)
        assertNotNull(span)
        verifyAndResetSessionUpdate()
        span.stop()
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `span started without a parent is a root span`() {
        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
        )

        assertNull(spanService.createdSpans.single().parent)
    }

    @Test
    fun `span started with a resolvable parent shares the same trace id with that parent`() {
        val parentSpan = FakeEmbraceSdkSpan.started()
        val parentSpanId = checkNotNull(parentSpan.spanId)
        spanService.addKnownSpan(parentSpan)

        val token = impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = parentSpanId,
        )

        val childTraceId = checkNotNull(token.asW3cTraceparent()).getTraceIdFromTraceparent()
        assertEquals(parentSpan.traceId, childTraceId)
    }

    @Test
    fun `span started without a parent that can be resolved has no parent`() {
        val parentSpan = FakeEmbraceSdkSpan.started()
        val parentSpanId = checkNotNull(parentSpan.spanId)

        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = parentSpanId,
        )

        assertNull(spanService.createdSpans.single().parent)
    }

    @Test
    fun `span started with an unknown parent and its trace id has a remote parent`() {
        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = REMOTE_SPAN_ID,
            parentTraceId = REMOTE_TRACE_ID,
        )

        val span = spanService.createdSpans.single()
        assertNull(span.parent)
        with(span.parentContext.extractSpan().spanContext) {
            assertEquals(REMOTE_TRACE_ID, traceId)
            assertEquals(REMOTE_SPAN_ID, spanId)
            assertTrue(isRemote)
            assertTrue(traceFlags.isSampled)
        }
    }

    @Test
    fun `known parent with a different trace id is used as a remote parent`() {
        val parentSpan = FakeEmbraceSdkSpan.started()
        spanService.addKnownSpan(parentSpan)
        val parentSpanId = checkNotNull(parentSpan.spanId)

        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = parentSpanId,
            parentTraceId = REMOTE_TRACE_ID,
        )

        val span = spanService.createdSpans.single()
        assertNull(span.parent)
        assertEquals(REMOTE_TRACE_ID, span.parentContext.extractSpan().spanContext.traceId)
    }

    @Test
    fun `known parent with a matching trace id is used as the embrace parent`() {
        val parentSpan = FakeEmbraceSdkSpan.started()
        spanService.addKnownSpan(parentSpan)

        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = checkNotNull(parentSpan.spanId),
            parentTraceId = checkNotNull(parentSpan.traceId),
        )

        assertEquals(parentSpan, spanService.createdSpans.single().parent)
    }

    @Test
    fun `invalid remote parent ids start a root span`() {
        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = REMOTE_SPAN_ID,
            parentTraceId = "00000000000000000000000000000000",
        )

        val span = spanService.createdSpans.single()
        assertNull(span.parent)
        assertFalse(span.parentContext.extractSpan().spanContext.isValid)
    }

    @Test
    fun `spans started without a network request ignore the network span parent`() {
        networkSpanParent = NetworkSpanParent.NO_PARENT
        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = REMOTE_SPAN_ID,
            parentTraceId = REMOTE_TRACE_ID,
        )

        assertTrue(askedParents.isEmpty())
        assertEquals(REMOTE_SPAN_ID, spanService.createdSpans.single().parentContext.extractSpan().spanContext.spanId)
    }

    @Test
    fun `a network span keeps its traceparent parent when the default rules apply`() {
        val parentSpan = FakeEmbraceSdkSpan.started()
        spanService.addKnownSpan(parentSpan)
        startNetworkSpan()
        startNetworkSpan(parentSpanId = checkNotNull(parentSpan.spanId), parentTraceId = checkNotNull(parentSpan.traceId))
        startNetworkSpan(parentSpanId = REMOTE_SPAN_ID, parentTraceId = REMOTE_TRACE_ID)

        val (noHeader, trackedHeader, remoteHeader) = spanService.createdSpans
        assertFalse(noHeader.parentContext.extractSpan().spanContext.isValid)
        assertEquals(parentSpan, trackedHeader.parent)
        assertEquals(REMOTE_SPAN_ID, remoteHeader.parentContext.extractSpan().spanContext.spanId)
        assertEquals(listOf(null, parentSpan.spanContext), askedParents.take(2))
        assertEquals(REMOTE_SPAN_ID, askedParents[2]?.spanId)
    }

    @Test
    fun `no parent makes a network span a root span despite a traceparent header`() {
        networkSpanParent = NetworkSpanParent.NO_PARENT
        startNetworkSpan(parentSpanId = REMOTE_SPAN_ID, parentTraceId = REMOTE_TRACE_ID)

        val span = spanService.createdSpans.single()
        assertNull(span.parent)
        assertFalse(span.parentContext.extractSpan().spanContext.isValid)
    }

    @Test
    fun `a chosen parent replaces a traceparent header and is marked sampled`() {
        networkSpanParent = NetworkSpanParent.parent(createSpanContext(CHOSEN_TRACE_ID, CHOSEN_SPAN_ID) { isSampled = false })
        startNetworkSpan(parentSpanId = REMOTE_SPAN_ID, parentTraceId = REMOTE_TRACE_ID)

        val span = spanService.createdSpans.single()
        assertNull(span.parent)
        with(span.parentContext.extractSpan().spanContext) {
            assertEquals(CHOSEN_TRACE_ID, traceId)
            assertEquals(CHOSEN_SPAN_ID, spanId)
            assertTrue(traceFlags.isSampled)
        }
    }

    @Test
    fun `network spans are parented to the network span parent unless they have a traceparent header`() {
        val service =
            NetworkSpanParentServiceImpl(spanService, FakeErrorHandler(), FakeInternalTelemetryService()) { "session" }
        impl = createDestination(service.provider)
        val checkout = FakeEmbraceSdkSpan.started().also(spanService::addKnownSpan)
        service.set(checkout.spanContext)
        startNetworkSpan()
        startNetworkSpan(parentSpanId = REMOTE_SPAN_ID, parentTraceId = REMOTE_TRACE_ID)

        val (child, headerChild) = spanService.createdSpans
        with(child.parentContext.extractSpan().spanContext) {
            assertEquals(checkout.traceId, traceId)
            assertEquals(checkout.spanId, spanId)
        }
        assertEquals(REMOTE_SPAN_ID, headerChild.parentContext.extractSpan().spanContext.spanId)
    }

    private fun startNetworkSpan(parentSpanId: String? = null, parentTraceId: String? = null) {
        impl.startSpanCapture(
            schemaType = SchemaType.NetworkRequest(emptyMap()),
            startTimeMs = 5,
            parentSpanId = parentSpanId,
            parentTraceId = parentTraceId,
            networkRequest = MutableHttpRequestInfoImpl("GET", "https://example.com"),
        )
    }

    @Test
    fun `test record successful span`() {
        val name = "success-span"
        val clock = FakeClock()
        val startTimeMs = clock.now()
        clock.tick(10)
        val endTimeMs = clock.now()
        impl.recordCompletedSpan(
            name,
            startTimeMs,
            endTimeMs,
            attributes = mapOf("foo" to "bar"),
            events = listOf(SpanEventImpl("event", clock.now().millisToNanos(), mapOf("key" to "value"))),
        )
        val span = spanService.createdSpans.single()
        assertEquals(name, span.name)
        assertEquals(startTimeMs, span.spanStartTimeMs)
        assertEquals(endTimeMs, span.spanEndTimeMs)
        assertEquals(StatusCode.UNSET, span.status.statusCode)
        assertTrue(span.hasEmbraceAttribute(EmbType.Performance.Default))
        assertFalse(span.attributes.containsKey(ErrorCodeAttribute.Failure.key))
        assertEquals("bar", span.attributes["foo"])
        val event = span.events.single()
        assertEquals("event", event.name)
        assertEquals(clock.now().millisToNanos(), event.timestampNanos)
        assertEquals("value", event.attributes["key"])
        assertNull(span.parent)
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test record failed span with attributes`() {
        val name = "failed-span"
        val startTimeMs = 5L
        val endTimeMs = 15L
        val errorCode = ErrorCodeAttribute.Failure
        val type = EmbType.Performance.Network
        val attributes = mapOf("foo" to "bar")
        impl.recordCompletedSpan(
            name = name,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            errorCode = errorCode,
            parent = null,
            type = type,
            internal = true,
            private = false,
            attributes = attributes,
        )
        val span = spanService.createdSpans.single()
        assertEquals(name, span.name)
        assertEquals(startTimeMs, span.spanStartTimeMs)
        assertEquals(endTimeMs, span.spanEndTimeMs)
        assertEquals(Span.Status.ERROR, span.status.statusCode.toEmbracePayload())
        assertTrue(span.hasEmbraceAttribute(type))
        assertTrue(span.hasEmbraceAttribute(errorCode))
        assertEquals(type, span.type)
        assertEquals(attributes + mapOf(type.asPair(), errorCode.asPair()), span.attributes)
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test start simple span`() {
        val name = "success-span"
        val startTimeMs = clock.now()
        val token = impl.startSpanCapture(
            name,
            startTimeMs,
        )
        token.stop()

        val span = spanService.createdSpans.single()
        assertEquals(name, span.name)
        assertEquals(startTimeMs, span.spanStartTimeMs)
        assertEquals(StatusCode.UNSET, span.status.statusCode)
        assertTrue(span.hasEmbraceAttribute(EmbType.Performance.Default))
        assertFalse(span.attributes.containsKey(ErrorCodeAttribute.Failure.key))
        assertNull(span.parent)
        verifyAndResetSessionUpdate()
    }

    @Test
    fun `test session part span events`() {
        currentSessionPartSpan.readySession()
        val current = checkNotNull(currentSessionPartSpan.current())
        val schemaType = SchemaType.Breadcrumb("Hi")
        impl.addSessionPartEvent(schemaType, 5)
        verifyAndResetSessionUpdate()

        val event = (current as FakeEmbraceSdkSpan).events.single()
        assertEquals("emb-${schemaType.fixedObjectName}", event.name)
    }

    @Test
    fun `session part span events are dropped when the event limit is reached`() {
        currentSessionPartSpan.readySession()
        val current = checkNotNull(currentSessionPartSpan.current())
        currentSessionPartSpan.canAddEventResult = false

        assertFalse(impl.addSessionPartEvent(SchemaType.Breadcrumb("Hi"), 5))
        assertTrue((current as FakeEmbraceSdkSpan).events.isEmpty())
        assertFalse(sessionDataUpdated)
    }

    @Test
    fun `only breadcrumbs are counted against the breadcrumb limit`() {
        currentSessionPartSpan.readySession()
        impl.addSessionPartEvent(SchemaType.Breadcrumb("Hi"), 5)
        impl.addSessionPartEvent(SchemaType.View("MyActivity"), 6)
        impl.addSessionPartEvent(
            SchemaType.PushNotification(title = "title", type = "notif", body = "body", id = "id", from = "from", priority = 1),
            7,
        )

        assertEquals(listOf(true, false, false), currentSessionPartSpan.canAddEventCalls)
    }

    @Test
    fun `test session part span attributes`() {
        currentSessionPartSpan.readySession()
        val current = checkNotNull(currentSessionPartSpan.current())
        impl.addSessionPartAttribute("foo", "bar")
        assertEquals("bar", current.attributes()["foo"])
        verifyAndResetSessionUpdate()

        impl.removeSessionPartAttribute("foo")
        assertNull(current.attributes()["foo"])
        verifyAndResetSessionUpdate()
    }

    private fun verifyAndResetSessionUpdate() {
        assertTrue(sessionDataUpdated)
        sessionDataUpdated = false
    }

    private companion object {
        const val REMOTE_TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
        const val REMOTE_SPAN_ID = "b7ad6b7169203331"
        const val CHOSEN_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736"
        const val CHOSEN_SPAN_ID = "00f067aa0ba902b7"
    }
}
