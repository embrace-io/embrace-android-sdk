package io.embrace.android.embracesdk.internal.logs

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.data.LogRecordData

internal interface LogsSink {

    fun storeLogs(logs: List<LogRecordData>): CompletableResultCode

    fun logs(): List<LogRecordData>

    fun flushLogs(): List<LogRecordData>
}