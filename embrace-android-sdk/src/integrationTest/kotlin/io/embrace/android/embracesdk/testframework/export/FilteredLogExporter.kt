package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * A [LogRecordExporter] used in the integration tests that allows retrieving exported logs
 * to perform assertions against.
 */
internal class FilteredLogExporter : LogRecordExporter {

    private val logData = mutableListOf<LogRecordData>()

    override fun export(logs: MutableCollection<LogRecordData>): CompletableResultCode {
        logData.addAll(logs)
        return CompletableResultCode.ofSuccess()
    }

    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }

    fun awaitLogExport(expectedCount: Int, filter: (LogRecordData) -> Boolean): List<LogRecordData> {
        val supplier = { logData.filter(filter) }
        return returnIfConditionMet(
            desiredValueSupplier = supplier,
            dataProvider = supplier,
            condition = { data ->
                data.size == expectedCount
            },
            errorMessageSupplier = {
                "Timeout. Expected $expectedCount logs, but got ${supplier().size}."
            }
        )
    }

}
