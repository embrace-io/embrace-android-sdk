package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeLoggerProvider
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeSpanService
import io.embrace.android.embracesdk.fakes.FakeTracerProvider
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.createNoopOpenTelemetry
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbTracerProviderTest {
    private val clock = FakeClock()
    private val openTelemetryClock = FakeOtelKotlinClock(clock)

    private lateinit var spanService: FakeSpanService
    private lateinit var sdkTracerProvider: FakeTracerProvider
    private lateinit var embTracerProvider: EmbTracerProvider

    @Before
    fun setup() {
        spanService = FakeSpanService()
        sdkTracerProvider = FakeTracerProvider()
        val otel = EmbOpenTelemetry(createNoopOpenTelemetry(), ::sdkTracerProvider) {
            FakeLoggerProvider()
        }
        embTracerProvider = EmbTracerProvider(
            impl = otel,
            spanService = spanService,
            clock = openTelemetryClock,
            useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK
        )
    }

    @Test
    fun `same instrumentation scope names return the same tracer instance`() {
        val tracer = embTracerProvider.getTracer("foo")
        val dupeTracer = embTracerProvider.getTracer("foo")
        val differentTracer = embTracerProvider.getTracer("food")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }

    @Test
    fun `same instrumentation scope version return the same tracer instance`() {
        val tracer = embTracerProvider.getTracer("foo", "v1")
        val dupeTracer = embTracerProvider.getTracer("foo", "v1")
        val differentTracer = embTracerProvider.getTracer("foo", "v2")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }

    @Test
    fun `same instrumentation schema url returns the same tracer instance`() {
        val tracer = embTracerProvider.getTracer("foo", schemaUrl = "url1")
        val dupeTracer = embTracerProvider.getTracer("foo", schemaUrl = "url1")
        val differentTracer = embTracerProvider.getTracer("foo", schemaUrl = "url2")
        assertSame(tracer, dupeTracer)
        assertNotSame(tracer, differentTracer)
    }
}
