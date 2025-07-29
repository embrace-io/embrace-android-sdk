package io.embrace.android.embracesdk.internal.otel.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakeOtelJavaTracer
import io.embrace.android.embracesdk.fakes.FakeOtelKotlinClock
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.k2j.tracing.TracerAdapter
import io.embrace.opentelemetry.kotlin.tracing.Tracer
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
        tracer = TracerAdapter(FakeOtelJavaTracer(), openTelemetryClock)
        embraceSpanFactory = EmbraceSpanFactoryImpl(
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            dataValidator = DataValidator()
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
        val spanParent = FakeEmbraceSdkSpan.started()
        val spanBuilder = OtelSpanStartArgs(
            name = "from-span-builder",
            type = EmbType.System.LowPower,
            internal = false,
            private = false,
            tracer = tracer,
            parentCtx = spanParent.createContext(),
        )

        with(embraceSpanFactory.create(otelSpanStartArgs = spanBuilder)) {
            assertTrue(start(clock.now()))
            assertTrue(hasEmbraceAttribute(EmbType.System.LowPower))
            assertEquals(spanParent, parent)
            assertFalse(hasEmbraceAttribute(PrivateSpan))
            assertEquals("from-span-builder", snapshot()?.name)
            assertTrue(updateNotified)
        }
    }
}
