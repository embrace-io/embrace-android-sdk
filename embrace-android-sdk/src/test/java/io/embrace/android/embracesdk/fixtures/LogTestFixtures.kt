package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.internal.logs.EmbraceLogBody
import io.embrace.android.embracesdk.internal.logs.EmbraceLogRecordData

internal val testLog = EmbraceLogRecordData(
    traceId = "ceadd56622414a06ae382e4e5a70bcf7",
    spanId = "50fbbe95362a430ba87ebd0132262ff1",
    timeUnixNanos = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = EmbraceLogBody(message = "test log message"),
    attributes = listOf(Pair("test1", "value1"), Pair("test2", "value2"))
)
