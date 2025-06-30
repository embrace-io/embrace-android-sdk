package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.arch.toPayload
import io.embrace.android.embracesdk.internal.otel.attrs.embProcessIdentifier
import io.embrace.android.embracesdk.internal.otel.attrs.embSendMode
import io.embrace.android.embracesdk.internal.otel.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.schema.SendMode
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.opentelemetry.semconv.incubating.SessionIncubatingAttributes

val testLog: Log = Log(
    traceId = "ceadd56622414a06ae382e4e5a70bcf7",
    spanId = "50fbbe95362a430ba87ebd0132262ff1",
    timeUnixNano = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "test log message",
    attributes = listOf(Attribute(key = "test1", data = "value1"), Attribute(key = "test2", data = "value2"))
)

val sendImmediatelyLog: Log = Log(
    traceId = null,
    spanId = null,
    timeUnixNano = 1681972471806000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "sendImmediately",
    attributes = listOf(Attribute(embSendMode.name, SendMode.IMMEDIATE.name))
)

val deferredLog: Log = Log(
    traceId = null,
    spanId = null,
    timeUnixNano = 1681972471807000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "deferred",
    attributes = listOf(Attribute(embSendMode.name, SendMode.DEFER.name))
)

val testNativeCrashData: NativeCrashData = NativeCrashData(
    nativeCrashId = "nativeCrashId",
    sessionId = "sessionId",
    timestamp = 1700000000000,
    crash = "base64binarystring",
    symbols = mapOf("key" to "value"),
)

val nativeCrashLog = Log(
    timeUnixNano = 1681972471806000000L,
    attributes = listOf(
        Attribute(embSendMode.name, SendMode.IMMEDIATE.name),
        EmbType.System.NativeCrash.toPayload(),
        Attribute(SessionIncubatingAttributes.SESSION_ID.key, "bb6b5b1ea2ff48928382fe81d7991ced"),
        Attribute(embProcessIdentifier.name, "8115ec91-3e5e-4d8a-816d-cc40306f9822"),
    )
)

val nativeCrashWithoutSessionLog = Log(
    timeUnixNano = 1681972481806000000L,
    attributes = listOf(
        Attribute(embSendMode.name, SendMode.IMMEDIATE.name),
        EmbType.System.NativeCrash.toPayload(),
    )
)
