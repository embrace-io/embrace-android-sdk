package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import io.opentelemetry.kotlin.NoopOpenTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PrivateTelemetryFilteringLogRecordProcessorTest {

    @Test
    fun `only non-private logs are forwarded`() {
        val delegate = FakeLogRecordProcessor()
        val processor = PrivateTelemetryFilteringLogRecordProcessor(delegate)
        listOf(
            FakeReadWriteLogRecord(body = "public"),
            FakeReadWriteLogRecord(body = "private").apply { setStringAttribute(PrivateSpan.key, PrivateSpan.value) },
        ).forEach { processor.onEmit(it, NoopOpenTelemetry.context.implicit()) }
        assertEquals(listOf("public"), delegate.processedLogBodies)
    }
}
