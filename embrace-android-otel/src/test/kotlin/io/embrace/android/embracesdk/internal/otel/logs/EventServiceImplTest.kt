package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryLogger
import io.opentelemetry.kotlin.context.ContextKey
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.logging.SeverityNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventServiceImplTest {
    private val otel = createOpenTelemetry()
    private val skipMetadataKey: ContextKey<Boolean> = otel.context.createKey("emb-skip-log-metadata")
    lateinit var sdkLogger: FakeOpenTelemetryLogger
    lateinit var impl: EventServiceImpl

    @Before
    fun setup() {
        sdkLogger = FakeOpenTelemetryLogger()
        impl = createEventService()
        impl.initializeService(100L)
    }

    @Test
    fun `event service needs initialization`() {
        val notInitializedLogger = createEventService()
        assertFalse(notInitializedLogger.initialized())
        notInitializedLogger.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = true,
        ) { }

        assertTrue(sdkLogger.logs.isEmpty())
    }

    @Test
    fun `event values forwarded to the sdk logger`() {
        assertTrue(impl.initialized())
        impl.log(
            eventName = "my.event",
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = true,
        ) {
            setStringAttribute("custom", "attr")
        }

        with(sdkLogger.logs.single()) {
            assertEquals("my.event", eventName)
            assertEquals(1000L, timestamp)
            assertEquals(1005L, observedTimestamp)
            assertEquals("test", body)
            assertEquals(SeverityNumber.ERROR, severityNumber)
            assertEquals("boo", severityText)
            assertEquals("attr", attributes["custom"])
        }
    }

    @Test
    fun `metadata enabled emits with the supplied context`() {
        impl.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = true,
        ) { }

        assertNull(sdkLogger.logs.single().context)
    }

    @Test
    fun `metadata disabled adds the skip-metadata key to the supplied context`() {
        val otherKey = otel.context.createKey<String>("other")
        val suppliedContext = otel.context.root().set(otherKey, "value")

        impl.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = suppliedContext,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = false,
        ) { }

        with(sdkLogger.logs.single().context) {
            assertEquals("value", this?.get(otherKey))
            assertEquals(true, this?.get(skipMetadataKey))
        }
    }

    @Test
    fun `metadata disabled with no supplied context falls back to the current context and adds the key`() {
        impl.log(
            eventName = null,
            body = "test",
            timestamp = 1000L,
            observedTimestamp = 1005L,
            context = null,
            severityNumber = SeverityNumber.ERROR,
            severityText = "boo",
            addCurrentMetadata = false,
        ) { }

        assertEquals(true, sdkLogger.logs.single().context?.get(skipMetadataKey))
    }

    private fun createEventService() = EventServiceImpl(
        sdkLoggerProvider = { sdkLogger },
        skipMetadataContextKey = { skipMetadataKey },
        implicitContextProvider = { otel.context.root() },
    )
}
