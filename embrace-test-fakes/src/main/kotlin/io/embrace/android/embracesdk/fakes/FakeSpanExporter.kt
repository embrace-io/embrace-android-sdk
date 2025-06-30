package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.tracing.export.SpanExporter
import io.embrace.opentelemetry.kotlin.tracing.model.ReadableSpan

@OptIn(ExperimentalApi::class)
class FakeSpanExporter : SpanExporter {

    val exportedSpans: MutableList<ReadableSpan> = mutableListOf()
    var flushCount: Int = 0
    var shutdownCount: Int = 0

    override fun export(telemetry: List<ReadableSpan>): OperationResultCode {
        exportedSpans += telemetry
        return OperationResultCode.Success
    }

    override fun forceFlush(): OperationResultCode {
        flushCount += 1
        return OperationResultCode.Success
    }

    override fun shutdown(): OperationResultCode {
        shutdownCount += 1
        return OperationResultCode.Success
    }
}
