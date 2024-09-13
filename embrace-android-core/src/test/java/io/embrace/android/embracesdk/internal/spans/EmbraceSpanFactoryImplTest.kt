package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakePersistableEmbraceSpan
import io.embrace.android.embracesdk.fakes.FakeTracer
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehaviorImpl
import io.embrace.android.embracesdk.internal.config.local.SdkLocalConfig
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
    private lateinit var tracer: FakeTracer

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        tracer = FakeTracer()
        embraceSpanFactory = EmbraceSpanFactoryImpl(
            tracer = tracer,
            openTelemetryClock = initModule.openTelemetryModule.openTelemetryClock,
            spanRepository = spanRepository,
            sensitiveKeysBehavior = SensitiveKeysBehaviorImpl(
                SdkLocalConfig(
                    sensitiveKeysDenylist = listOf("password")
                )
            )
        )
    }

    @Test
    fun `check public span creation`() {
        val span = embraceSpanFactory.create(name = "test", type = EmbType.Performance.Default, internal = false, private = false)
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasFixedAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertFalse(hasFixedAttribute(PrivateSpan))
            assertEquals("test", snapshot()?.name)
        }
        assertNotNull(spanRepository.getSpan(spanId = checkNotNull(span.spanId)))
    }

    @Test
    fun `check internal span creation`() {
        val span = embraceSpanFactory.create(name = "test", type = EmbType.Performance.Default, internal = true, private = true)
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasFixedAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertTrue(hasFixedAttribute(PrivateSpan))
            assertEquals("emb-test", snapshot()?.name)
        }
    }

    @Test
    fun `check internal span can be public`() {
        val span = embraceSpanFactory.create(name = "test", type = EmbType.Performance.Default, internal = true, private = false)
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasFixedAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertFalse(hasFixedAttribute(PrivateSpan))
            assertEquals("emb-test", snapshot()?.name)
        }
    }

    @Test
    fun `span creation with embrace span builder`() {
        val spanParent = FakePersistableEmbraceSpan.started()
        val spanBuilder = tracer.embraceSpanBuilder(
            name = "from-span-builder",
            type = EmbType.System.LowPower,
            internal = false,
            private = false,
            parent = spanParent
        )

        with(embraceSpanFactory.create(embraceSpanBuilder = spanBuilder)) {
            assertTrue(start(clock.now()))
            assertTrue(hasFixedAttribute(EmbType.System.LowPower))
            assertEquals(spanParent, parent)
            assertFalse(hasFixedAttribute(PrivateSpan))
            assertEquals("from-span-builder", snapshot()?.name)
        }
    }
}
