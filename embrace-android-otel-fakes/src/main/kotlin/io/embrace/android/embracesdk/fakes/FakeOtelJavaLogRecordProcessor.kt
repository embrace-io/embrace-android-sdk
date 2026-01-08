package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.aliases.OtelJavaCompletableResultCode
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaContext
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaLogRecordProcessor
import io.embrace.opentelemetry.kotlin.aliases.OtelJavaReadWriteLogRecord

class FakeOtelJavaLogRecordProcessor : OtelJavaLogRecordProcessor {

    var logCount: Int = 0

    override fun onEmit(context: OtelJavaContext, logRecord: OtelJavaReadWriteLogRecord) {
        logCount++
    }

    override fun forceFlush(): OtelJavaCompletableResultCode = OtelJavaCompletableResultCode.ofSuccess()

    override fun shutdown(): OtelJavaCompletableResultCode = OtelJavaCompletableResultCode.ofSuccess()
}
