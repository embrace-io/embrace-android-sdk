package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeEmbraceSdkSpan
import io.embrace.android.embracesdk.fakes.FakePayloadCachingService
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.config.FakeRedactionConfig
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.opentelemetry.embraceSpanBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class EmbraceSpanFactoryImplTest {
    private val clock = FakeClock()
    private lateinit var embraceSpanFactory: EmbraceSpanFactoryImpl
    private lateinit var spanRepository: SpanRepository
    private lateinit var cachingService: FakePayloadCachingService
    private lateinit var tracer: FakeTracer
    private var updateNotified: Boolean = false

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock)
        spanRepository = initModule.openTelemetryModule.spanRepository.apply {
            setSpanUpdateNotifier {
                updateNotified = true
            }
        }
        tracer = FakeTracer()
        cachingService = FakePayloadCachingService()
        embraceSpanFactory = EmbraceSpanFactoryImpl(
            tracer = tracer,
            openTelemetryClock = initModule.openTelemetryModule.openTelemetryClock,
            spanRepository = spanRepository,
            sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
                FakeInstrumentedConfig(redaction = FakeRedactionConfig(sensitiveKeys = listOf("password"))),
            ),
        )
    }

    @Test
    fun `check public span creation`() {
        val span = embraceSpanFactory.create(
            name = "test",
            type = EmbType.Performance.Default,
            internal = false,
            private = false,
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
            name = "test",
            type = EmbType.Performance.Default,
            internal = true,
            private = true,
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
            name = "test",
            type = EmbType.Performance.Default,
            internal = true,
            private = false,
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
        val spanBuilder = tracer.embraceSpanBuilder(
            name = "from-span-builder",
            type = EmbType.System.LowPower,
            internal = false,
            private = false,
            parent = spanParent,
        )

        with(embraceSpanFactory.create(otelSpanBuilderWrapper = spanBuilder)) {
            assertTrue(start(clock.now()))
            assertTrue(hasEmbraceAttribute(EmbType.System.LowPower))
            assertEquals(spanParent, parent)
            assertFalse(hasEmbraceAttribute(PrivateSpan))
            assertEquals("from-span-builder", snapshot()?.name)
            assertTrue(updateNotified)
        }
    }
}
