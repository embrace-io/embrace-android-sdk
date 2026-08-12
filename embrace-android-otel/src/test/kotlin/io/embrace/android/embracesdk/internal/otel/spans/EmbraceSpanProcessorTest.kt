package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeReadWriteSpan
import io.embrace.android.embracesdk.fakes.FakeSessionIdsProvider
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.semconv.UserAttributes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class EmbraceSpanProcessorTest {

    private val processIdentifier = "pid"
    private val context = NoopOpenTelemetry.context.implicit()

    private lateinit var spanExporter: FakeSpanExporter
    private lateinit var sessionIdsProvider: FakeSessionIdsProvider
    private lateinit var span: FakeReadWriteSpan

    @Before
    fun setup() {
        spanExporter = FakeSpanExporter()
        sessionIdsProvider = FakeSessionIdsProvider(userSessionId = "user-sid", sessionPartId = "part-sid")
        span = FakeReadWriteSpan()
    }

    @Test
    fun `test export`() {
        val processor = createProcessor()
        processor.onStart(span, context)
        assertEquals("pid", span.attributes[EmbSessionAttributes.EMB_PROCESS_IDENTIFIER])
        assertEquals("user-sid", span.attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
        assertEquals("part-sid", span.attributes[EmbSessionAttributes.EMB_SESSION_PART_ID])
        processor.onEnd(span)
        assertEquals(span, spanExporter.exportedSpans.single())
    }

    @Test
    fun `empty string set for session attributes when ids are absent`() {
        sessionIdsProvider.userSessionId = ""
        sessionIdsProvider.sessionPartId = ""
        val processor = createProcessor()
        processor.onStart(span, context)

        assertEquals("", span.attributes[EmbSessionAttributes.EMB_SESSION_PART_ID])
        assertEquals("", span.attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
    }

    @Test
    fun `empty string set for user session id when only session part id is absent`() {
        sessionIdsProvider.userSessionId = ""
        val processor = createProcessor()
        processor.onStart(span, context)

        assertEquals("part-sid", span.attributes[EmbSessionAttributes.EMB_SESSION_PART_ID])
        assertEquals("", span.attributes[EmbSessionAttributes.EMB_USER_SESSION_ID])
    }

    @Test
    fun `user id is stamped on span when provider returns a value`() {
        val processor = createProcessor(userIdProvider = { "user-123" })
        processor.onStart(span, context)
        assertEquals("user-123", span.attributes[UserAttributes.USER_ID])
    }

    @Test
    fun `user id attribute is absent when provider returns null`() {
        val processor = createProcessor()
        processor.onStart(span, context)
        assertFalse(span.attributes.containsKey(UserAttributes.USER_ID))
    }

    @Test
    fun `onEnd() exports before returning, so ending a session part can drain the spans immediately`() {
        val processor = createProcessor()

        processor.onEnd(span)

        // no drain and no waiting: the span has to be with the exporter by the time onEnd returns,
        // otherwise a session payload can ship without its own session span
        assertEquals(span, spanExporter.exportedSpans.single())
    }

    @Test
    fun `forceFlush() flushes the exporter, which is what drains export dispatched off-thread`() {
        val processor = createProcessor()

        val result = runBlocking { processor.forceFlush() }

        assertEquals(OperationResultCode.Success, result)
        assertEquals(1, spanExporter.forceFlushCount)
    }

    private fun createProcessor(userIdProvider: () -> String? = { null }) =
        EmbraceSpanProcessor(
            sessionIdsProvider = { sessionIdsProvider },
            userIdProvider = userIdProvider,
            processIdentifier = processIdentifier,
            spanExporter = spanExporter,
        )
}
