package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogBody

internal val testLog = Log(
    traceId = "ceadd56622414a06ae382e4e5a70bcf7",
    spanId = "50fbbe95362a430ba87ebd0132262ff1",
    timeUnixNano = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = LogBody(message = "test log message"),
    attributes = listOf(Attribute(key = "test1", data = "value1"), Attribute(key = "test2", data = "value2"))
)
