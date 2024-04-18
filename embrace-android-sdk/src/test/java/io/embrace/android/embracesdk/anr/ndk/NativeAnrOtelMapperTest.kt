package io.embrace.android.embracesdk.anr.ndk

import io.embrace.android.embracesdk.config.remote.AnrRemoteConfig
import io.embrace.android.embracesdk.fakes.FakeNativeThreadSamplerService
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Span
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.payload.NativeThreadAnrInterval
import io.embrace.android.embracesdk.payload.NativeThreadAnrSample
import io.embrace.android.embracesdk.payload.NativeThreadAnrStackframe
import io.embrace.android.embracesdk.payload.ThreadState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class NativeAnrOtelMapperTest {

    private lateinit var mapper: NativeAnrOtelMapper
    private lateinit var service: FakeNativeThreadSamplerService

    @Before
    fun setUp() {
        service = FakeNativeThreadSamplerService()
        mapper = NativeAnrOtelMapper(service, EmbraceSerializer())
    }

    @Test
    fun `null intervals`() {
        assertEquals(emptyList<Span>(), mapper.snapshot(false))
    }

    @Test
    fun `empty intervals`() {
        service.intervals = emptyList()
        assertEquals(emptyList<Span>(), mapper.snapshot(false))
    }

    @Test
    fun `has intervals`() {
        service.intervals = listOf(
            NativeThreadAnrInterval(
                id = 1,
                name = "main",
                priority = 5,
                threadState = ThreadState.BLOCKED,
                sampleOffsetMs = 100,
                unwinderType = AnrRemoteConfig.Unwinder.LIBUNWINDSTACK,
                threadBlockedTimestamp = 1000L,
                samples = mutableListOf(
                    NativeThreadAnrSample(
                        0,
                        2000L,
                        5,
                        listOf(
                            NativeThreadAnrStackframe(
                                "0x8000050209",
                                "0x500234500",
                                "/data/app/lib.so",
                                0
                            )
                        )
                    )
                )
            )
        )
        val spans = mapper.snapshot(false)
        val span = spans.single()

        // assert span
        assertEquals("emb_native_thread_blockage", span.name)
        assertEquals(1000L.millisToNanos(), span.startTimeUnixNano)

        // assert span attrs
        val attrs = checkNotNull(span.attributes)
        assertEquals("perf.native_thread_blockage", attrs.findAttribute("emb.type").data)
        assertEquals("1", attrs.findAttribute("thread_id").data)
        assertEquals("main", attrs.findAttribute("thread_name").data)
        assertEquals("2", attrs.findAttribute("thread_state").data)
        assertEquals("100", attrs.findAttribute("sampling_offset_ms").data)
        assertEquals("1", attrs.findAttribute("stack_unwinder").data)

        // assert span event
        val event = checkNotNull(span.events?.single())
        assertEquals("emb_native_thread_blockage_sample", event.name)
        assertEquals(2000L.millisToNanos(), event.timeUnixNano)
        val eventAttrs = checkNotNull(event.attributes)
        assertEquals("0", eventAttrs.findAttribute("result").data)
        assertEquals("5", eventAttrs.findAttribute("sample_overhead_ms").data)
        assertEquals("perf.native_thread_blockage_sample", eventAttrs.findAttribute("emb.type").data)

        val expectedStacktrace = "[{\"program_counter\":\"0x8000050209\",\"so_load_addr\":\"0x500234500\"," +
            "\"so_name\":\"/data/app/lib.so\",\"result\":0}]"
        assertEquals(expectedStacktrace, eventAttrs.findAttribute("stacktrace").data)
    }

    private fun List<Attribute>.findAttribute(key: String): Attribute {
        return single { it.key == key }
    }
}
