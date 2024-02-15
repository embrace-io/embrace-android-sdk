package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

internal interface LogSink {

    fun storeLogs(logs: List<LogRecordData>): CompletableResultCode

    fun logs(): List<EmbraceLogRecordData>

    fun flushLogs(): List<EmbraceLogRecordData>
}