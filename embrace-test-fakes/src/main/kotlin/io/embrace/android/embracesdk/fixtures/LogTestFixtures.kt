package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.fakes.FakeLogRecordData
import io.embrace.android.embracesdk.internal.arch.schema.SendMode
import io.embrace.android.embracesdk.internal.opentelemetry.embSendMode
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.NativeCrashDataError

public val testLog: Log = Log(
    traceId = "ceadd56622414a06ae382e4e5a70bcf7",
    spanId = "50fbbe95362a430ba87ebd0132262ff1",
    timeUnixNano = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "test log message",
    attributes = listOf(Attribute(key = "test1", data = "value1"), Attribute(key = "test2", data = "value2"))
)

public val sendImmediatelyLog: Log = Log(
    traceId = null,
    spanId = null,
    timeUnixNano = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "sendImmediately",
    attributes = listOf(Attribute(embSendMode.name, SendMode.IMMEDIATE.name))
)

public val deferredLog: Log = Log(
    traceId = null,
    spanId = null,
    timeUnixNano = 1681972471807000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "deferred",
    attributes = listOf(Attribute(embSendMode.name, SendMode.DEFER.name))
)

public val sendImmediatelyLogRecordData: FakeLogRecordData = FakeLogRecordData(log = sendImmediatelyLog)

public val deferredLogRecordData: FakeLogRecordData = FakeLogRecordData(log = deferredLog)

public val testNativeCrashData: NativeCrashData = NativeCrashData(
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
