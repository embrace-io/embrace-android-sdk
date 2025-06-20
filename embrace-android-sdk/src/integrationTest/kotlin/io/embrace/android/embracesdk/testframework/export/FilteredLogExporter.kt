package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.assertions.returnIfConditionMet
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter

internal class FilteredLogExporter: OtelJavaLogRecordExporter {

    private val logData = mutableListOf<OtelJavaLogRecordData>()

    override fun export(logs: MutableCollection<OtelJavaLogRecordData>): OtelJavaCompletableResultCode {
        logData.addAll(logs)
        return OtelJavaCompletableResultCode.ofSuccess()
    }

    override fun flush(): OtelJavaCompletableResultCode {
        return OtelJavaCompletableResultCode.ofSuccess()
    }

    override fun shutdown(): OtelJavaCompletableResultCode {
        return OtelJavaCompletableResultCode.ofSuccess()
    }

    fun awaitLogs(expectedCount: Int, filter: (OtelJavaLogRecordData) -> Boolean): List<OtelJavaLogRecordData> {
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
