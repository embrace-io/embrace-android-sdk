package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.export.OperationResultCode
import io.embrace.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.embrace.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

@OptIn(ExperimentalApi::class)
class FakeLogRecordProcessor(
    private val onEmitAction: (ReadWriteLogRecord) -> Unit = {},
) : LogRecordProcessor {

    val processedLogBodies = mutableListOf<String>()

    override fun onEmit(
        log: ReadWriteLogRecord,
        context: Context,
    ) {
        val body = log.body ?: ""
        processedLogBodies.add(body)
        onEmitAction(log)
    }

    override fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override fun shutdown(): OperationResultCode = OperationResultCode.Success
}
