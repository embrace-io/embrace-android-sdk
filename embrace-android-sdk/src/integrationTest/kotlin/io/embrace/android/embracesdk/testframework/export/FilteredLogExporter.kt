package io.embrace.android.embracesdk.testframework.export

import io.embrace.android.embracesdk.testframework.ChangeSignal
import io.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordData
import io.opentelemetry.kotlin.aliases.OtelJavaLogRecordExporter
import java.util.concurrent.CopyOnWriteArrayList

internal class FilteredLogExporter: OtelJavaLogRecordExporter {

    private val logData = CopyOnWriteArrayList<OtelJavaLogRecordData>()
    private val exported = ChangeSignal()

    override fun export(logs: MutableCollection<OtelJavaLogRecordData>): OtelJavaCompletableResultCode {
        logData.addAll(logs)
        exported.notifyChanged()
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
        return exported.await(
            supplier = supplier,
            condition = { data ->
                data.size == expectedCount
            },
            errorMessageSupplier = {
                "Timeout. Expected $expectedCount logs, but got ${supplier().size}."
            }
        )
    }
}
