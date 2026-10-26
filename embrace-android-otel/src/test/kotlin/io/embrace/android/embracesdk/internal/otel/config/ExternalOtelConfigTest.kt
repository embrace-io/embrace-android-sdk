package io.embrace.android.embracesdk.internal.otel.config

import io.embrace.android.embracesdk.fakes.FakeLogRecordExporter
import io.embrace.android.embracesdk.fakes.FakeLogRecordProcessor
import io.embrace.android.embracesdk.fakes.FakeSpanExporter
import io.embrace.android.embracesdk.fakes.FakeSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Test

internal class ExternalOtelConfigTest {

    /**
     * The app can add to the config on another thread while the SDK reads it during start. A read that iterates a list while
     * an add lands must finish with what it started with, not throw.
     */
    @Test
    fun `adding while a read iterates does not disturb the read`() {
        val config = ExternalOtelConfig()
        val first = Components()
        val second = Components()
        first.addTo(config)

        val spanExporters = config.getSpanExporters().iterator()
        val spanProcessors = config.getSpanProcessors().iterator()
        val logExporters = config.getLogExporters().iterator()
        val logRecordProcessors = config.getLogRecordProcessors().iterator()
        second.addTo(config)

        assertEquals(listOf(first.spanExporter), spanExporters.asSequence().toList())
        assertEquals(listOf(first.spanProcessor), spanProcessors.asSequence().toList())
        assertEquals(listOf(first.logExporter), logExporters.asSequence().toList())
        assertEquals(listOf(first.logRecordProcessor), logRecordProcessors.asSequence().toList())

        // later reads see the add
        assertEquals(listOf(first.spanExporter, second.spanExporter), config.getSpanExporters())
        assertEquals(listOf(first.spanProcessor, second.spanProcessor), config.getSpanProcessors())
        assertEquals(listOf(first.logExporter, second.logExporter), config.getLogExporters())
        assertEquals(listOf(first.logRecordProcessor, second.logRecordProcessor), config.getLogRecordProcessors())
    }

    @Test
    fun `otlp export is configured once anything is added`() {
        val config = ExternalOtelConfig()
        assertEquals(false, config.hasConfiguredOtlpExport())
        config.addLogRecordProcessor(FakeLogRecordProcessor())
        assertEquals(true, config.hasConfiguredOtlpExport())
    }

    private class Components {
        val spanExporter = FakeSpanExporter()
        val spanProcessor = FakeSpanProcessor()
        val logExporter = FakeLogRecordExporter()
        val logRecordProcessor = FakeLogRecordProcessor()

        fun addTo(config: ExternalOtelConfig) {
            config.addSpanExporter(spanExporter)
            config.addSpanProcessor(spanProcessor)
            config.addLogExporter(logExporter)
            config.addLogRecordProcessor(logRecordProcessor)
        }
    }
}
