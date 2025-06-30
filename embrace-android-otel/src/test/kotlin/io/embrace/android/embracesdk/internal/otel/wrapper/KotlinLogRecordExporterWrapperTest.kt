package io.embrace.android.embracesdk.internal.otel.wrapper

import io.embrace.android.embracesdk.assertions.toMap
import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.sdk.common.CompletableResultCode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalApi::class)
class KotlinLogRecordExporterWrapperTest {

    private lateinit var impl: FakeLogRecordExporter
    private lateinit var wrapper: KotlinLogRecordExporterWrapper

    @Before
    fun setUp() {
        impl = FakeLogRecordExporter()
        wrapper = KotlinLogRecordExporterWrapper(impl)
    }

    @Test
    fun export() {
        val data = FakeLogRecordData()
        wrapper.export(mutableListOf(data))
        val result = impl.exportedLogs.single()

        val original = data.log
        assertEquals(original.body, result.body)
        assertEquals(original.timeUnixNano, result.timestamp)
        assertEquals(original.severityNumber, result.severityNumber?.severityNumber)
        assertEquals(original.severityText, result.severityText)
        assertEquals(original.traceId, result.traceId)
        assertEquals(original.spanId, result.spanId)
        assertEquals(original.attributes?.toMap(), result.attributes)
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
