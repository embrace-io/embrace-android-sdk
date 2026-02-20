package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeEventService
import io.embrace.android.embracesdk.fakes.FakeLoggerProvider
import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbLoggerProviderTest {
    private lateinit var eventService: FakeEventService
    private lateinit var sdkLoggerProvider: FakeLoggerProvider
    private lateinit var embLoggerProvider: EmbLoggerProvider

    @Before
    fun setup() {
        eventService = FakeEventService()
        sdkLoggerProvider = FakeLoggerProvider()
        val otel = EmbOpenTelemetry(
            NoopOpenTelemetry,
            { FakeTracerProvider() },
            ::sdkLoggerProvider
        )
        embLoggerProvider = EmbLoggerProvider(
            otelImpl = otel,
            eventService = eventService,
        )
    }

    @Test
    fun `same instrumentation scope names return the same logger instance`() {
        val logger = embLoggerProvider.getLogger("foo")
        val dupeLogger = embLoggerProvider.getLogger("foo")
        val differentLogger = embLoggerProvider.getLogger("food")
        assertSame(logger, dupeLogger)
        assertNotSame(logger, differentLogger)
    }

    @Test
    fun `same instrumentation scope version return the same logger instance`() {
        val logger = embLoggerProvider.getLogger("foo", "v1")
        val dupeLogger = embLoggerProvider.getLogger("foo", "v1")
        val differentLogger = embLoggerProvider.getLogger("foo", "v2")
        assertSame(logger, dupeLogger)
        assertNotSame(logger, differentLogger)
    }

    @Test
    fun `same instrumentation schema url returns the same logger instance`() {
        val logger = embLoggerProvider.getLogger("foo", schemaUrl = "url1")
        val dupeLogger = embLoggerProvider.getLogger("foo", schemaUrl = "url1")
        val differentLogger = embLoggerProvider.getLogger("foo", schemaUrl = "url2")
        assertSame(logger, dupeLogger)
        assertNotSame(logger, differentLogger)
    }
}
