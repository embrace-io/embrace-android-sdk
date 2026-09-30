package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeReadWriteLogRecord
import io.embrace.android.embracesdk.internal.arch.schema.PrivateSpan
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

internal class PrivateTelemetryFilteringLogRecordExporterTest {

    @Test
    fun `only non-private logs are forwarded`() {
        val delegate = FakeLogRecordExporter()
        val exporter = PrivateTelemetryFilteringLogRecordExporter(delegate)
        val logs = listOf(
            FakeReadWriteLogRecord(body = "public"),
            FakeReadWriteLogRecord(body = "private").apply { setStringAttribute(PrivateSpan.key, PrivateSpan.value) },
        )
        runBlocking { exporter.export(logs) }
        assertEquals(listOf("public"), delegate.exportedLogs.map { it.body })
    }
}
