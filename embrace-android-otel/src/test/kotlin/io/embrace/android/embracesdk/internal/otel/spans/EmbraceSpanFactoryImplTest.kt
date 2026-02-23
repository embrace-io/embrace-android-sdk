package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.fakes.FakeTelemetryService
import io.embrace.android.embracesdk.fakes.TestConstants.TESTS_DEFAULT_USE_KOTLIN_SDK
import io.embrace.android.embracesdk.fakes.fakeOpenTelemetry
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.createSdkOtelInstance
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.getTracer
import io.opentelemetry.kotlin.tracing.Tracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
internal class EmbraceSpanFactoryImplTest {
    private val clock = FakeClock()
    private lateinit var embraceSpanFactory: EmbraceSpanFactoryImpl
    private lateinit var spanRepository: SpanRepository
    private lateinit var tracer: Tracer
    private var updateNotified: Boolean = false

    @Before
    fun setup() {
        val openTelemetryClock = FakeOtelKotlinClock(clock)
        spanRepository = SpanRepository().apply {
            setSpanUpdateNotifier {
                updateNotified = true
            }
        }
        tracer = createSdkOtelInstance(clock = openTelemetryClock, useKotlinSdk = TESTS_DEFAULT_USE_KOTLIN_SDK).getTracer("my_tracer")
        embraceSpanFactory = EmbraceSpanFactoryImpl(
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            dataValidator = DataValidator(telemetryService = FakeTelemetryService()),
            telemetryService = FakeTelemetryService()
        )
    }

    @Test
    fun `check public span creation`() {
        val span = embraceSpanFactory.create(
            OtelSpanStartArgs(
                name = "test",
                type = EmbType.Performance.Default,
                internal = false,
                private = false,
                tracer = tracer,
                openTelemetry = fakeOpenTelemetry(),
            )
        )
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasEmbraceAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertFalse(hasEmbraceAttribute(PrivateSpan))
            assertEquals("test", snapshot()?.name)
        }
        assertNotNull(spanRepository.getSpan(spanId = checkNotNull(span.spanId)))
        assertTrue(updateNotified)
    }

    @Test
    fun `check internal span creation`() {
        val span = embraceSpanFactory.create(
            OtelSpanStartArgs(
                name = "test",
                type = EmbType.Performance.Default,
                internal = true,
                private = true,
                tracer = tracer,
                openTelemetry = fakeOpenTelemetry(),
            )
        )
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasEmbraceAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertTrue(hasEmbraceAttribute(PrivateSpan))
            assertEquals("emb-test", snapshot()?.name)
        }
    }

    @Test
    fun `check internal span can be public`() {
        val span = embraceSpanFactory.create(
            OtelSpanStartArgs(
                name = "test",
                type = EmbType.Performance.Default,
                internal = true,
                private = false,
                tracer = tracer,
                openTelemetry = fakeOpenTelemetry(),
            )
        )
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasEmbraceAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertFalse(hasEmbraceAttribute(PrivateSpan))
            assertEquals("emb-test", snapshot()?.name)
        }
    }

    @Test
    fun `span creation with embrace span builder`() {
        val parent = embraceSpanFactory.create(
            OtelSpanStartArgs(
                name = "",
                type = EmbType.System.Log,
                internal = false,
                private = false,
                tracer = tracer,
                openTelemetry = fakeOpenTelemetry(),
            )
        )
        val spanBuilder = OtelSpanStartArgs(
            name = "from-span-builder",
            type = EmbType.System.LowPower,
            internal = false,
            private = false,
            tracer = tracer,
            parentCtx = parent.asNewContext(),
            openTelemetry = fakeOpenTelemetry(),
        )

        with(embraceSpanFactory.create(otelSpanStartArgs = spanBuilder)) {
            assertTrue(start(clock.now()))
            assertTrue(hasEmbraceAttribute(EmbType.System.LowPower))
            assertEquals(parent.spanContext?.traceId, parent.spanContext?.traceId)
            assertFalse(hasEmbraceAttribute(PrivateSpan))
            assertEquals("from-span-builder", snapshot()?.name)
            assertTrue(updateNotified)
        }
    }
}
