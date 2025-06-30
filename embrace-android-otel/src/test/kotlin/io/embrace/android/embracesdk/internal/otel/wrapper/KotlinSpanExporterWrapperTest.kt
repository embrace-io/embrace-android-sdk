package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.android.embracesdk.fakes.FakeSpanData
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
class KotlinSpanExporterWrapperTest {

    private lateinit var impl: FakeSpanExporter
    private lateinit var wrapper: KotlinSpanExporterWrapper

    @Before
    fun setUp() {
        impl = FakeSpanExporter()
        wrapper = KotlinSpanExporterWrapper(impl)
    }

    @Test
    fun export() {
        val original = FakeSpanData()
        wrapper.export(mutableListOf(original))
        val result = impl.exportedSpans.single()

        assertEquals(original.name, result.name)
        TODO("Assert other properties")
    }

    @Test
    fun flush() {
        assertEquals(CompletableResultCode.ofSuccess(), wrapper.flush())
        assertEquals(1, impl.flushCount)
    }

    @Test
    fun shutdown() {
        assertEquals(CompletableResultCode.ofSuccess(), wrapper.shutdown())
        assertEquals(1, impl.shutdownCount)
    }
}
