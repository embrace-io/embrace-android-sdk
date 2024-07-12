package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.internal.arch.schema.SendImmediately
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.payload.NativeCrashData
import io.embrace.android.embracesdk.payload.NativeCrashDataError

internal val testLog = Log(
    traceId = "ceadd56622414a06ae382e4e5a70bcf7",
    spanId = "50fbbe95362a430ba87ebd0132262ff1",
    timeUnixNano = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "test log message",
    attributes = listOf(Attribute(key = "test1", data = "value1"), Attribute(key = "test2", data = "value2"))
)

internal val nonbatchableLog = Log(
    traceId = null,
    spanId = null,
    timeUnixNano = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "unbatchable",
    attributes = listOf(SendImmediately.toPayload())
)

internal val unbatchableLogRecordData = FakeLogRecordData(log = nonbatchableLog)

internal val testNativeCrashData = NativeCrashData(
    nativeCrashId = "nativeCrashId",
    sessionId = "sessionId",
    timestamp = 1700000000000,
    appState = null,
    metadata = null,
    unwindError = 5,
    crash = "base64binarystring",
    symbols = mapOf("key" to "value"),
    errors = listOf(
        NativeCrashDataError(
            6,
            7
        )
    ),
    map = null
)
