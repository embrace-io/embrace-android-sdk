package io.embrace.android.embracesdk.internal.spans

import io.embrace.android.embracesdk.arch.schema.EmbType
import io.embrace.android.embracesdk.arch.schema.PrivateSpan
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
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

    @Before
    fun setup() {
        val initModule = FakeInitModule(clock)
        spanRepository = initModule.openTelemetryModule.spanRepository
        embraceSpanFactory = EmbraceSpanFactoryImpl(
            tracer = initModule.openTelemetryModule.tracer,
            openTelemetryClock = initModule.openTelemetryClock,
            spanRepository = spanRepository
        )
    }

    @Test
    fun `check public span creation`() {
        val span = embraceSpanFactory.create(name = "test", type = EmbType.Performance.Default, internal = false)
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasEmbraceAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertFalse(hasEmbraceAttribute(PrivateSpan))
            assertEquals("test", snapshot()?.name)
        }
        assertNotNull(spanRepository.getSpan(spanId = checkNotNull(span.spanId)))
    }

    @Test
    fun `check internal span creation`() {
        val span = embraceSpanFactory.create(name = "test", type = EmbType.Performance.Default, internal = true)
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
        val span = embraceSpanFactory.create(name = "test", type = EmbType.Performance.Default, internal = true, private = false)
        assertTrue(span.start(clock.now()))
        with(span) {
            assertTrue(hasEmbraceAttribute(EmbType.Performance.Default))
            assertNull(parent)
            assertFalse(hasEmbraceAttribute(PrivateSpan))
            assertEquals("emb-test", snapshot()?.name)
        }
    }
}
