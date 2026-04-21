package io.embrace.android.embracesdk.fixtures

import io.embrace.android.embracesdk.assertions.toPayload
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.arch.schema.SendMode
import io.embrace.android.embracesdk.internal.payload.Attribute
import io.embrace.android.embracesdk.internal.payload.Log

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
    attributes = listOf(Attribute(EmbSessionAttributes.EMB_PRIVATE_SEND_MODE, SendMode.IMMEDIATE.name))
)

val deferredLog: Log = Log(
    traceId = null,
    spanId = null,
    timeUnixNano = 1681972471807000000L,
    severityNumber = 9,
    severityText = "INFO",
    body = "deferred",
    attributes = listOf(Attribute(EmbSessionAttributes.EMB_PRIVATE_SEND_MODE, SendMode.DEFER.name))
)

val nativeCrashLog = Log(
    timeUnixNano = 1681972471806000000L,
    attributes = listOf(
        Attribute(EmbSessionAttributes.EMB_PRIVATE_SEND_MODE, SendMode.IMMEDIATE.name),
        EmbType.System.NativeCrash.toPayload(),
        Attribute(EmbSessionAttributes.EMB_SESSION_PART_ID, "bb6b5b1ea2ff48928382fe81d7991ced"),
        Attribute(EmbSessionAttributes.EMB_PROCESS_IDENTIFIER, "8115ec91-3e5e-4d8a-816d-cc40306f9822"),
    )
)

val nativeCrashWithoutSessionLog = Log(
    timeUnixNano = 1681972481806000000L,
    attributes = listOf(
        Attribute(EmbSessionAttributes.EMB_PRIVATE_SEND_MODE, SendMode.IMMEDIATE.name),
        EmbType.System.NativeCrash.toPayload(),
    )
)
