package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord

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

    override suspend fun forceFlush(): OperationResultCode = OperationResultCode.Success
    override suspend fun shutdown(): OperationResultCode = OperationResultCode.Success
}
