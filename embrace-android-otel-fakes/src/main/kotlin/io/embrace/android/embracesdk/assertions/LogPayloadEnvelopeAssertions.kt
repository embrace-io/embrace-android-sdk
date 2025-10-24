package io.embrace.android.embracesdk.assertions

import io.embrace.android.embracesdk.internal.arch.schema.EmbType
import io.embrace.android.embracesdk.internal.otel.sdk.findAttributeValue
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload

fun Envelope<LogPayload>.getLastLog(): Log {
    return checkNotNull(data.logs).last()
}

fun Envelope<LogPayload>.getLogs(predicate: (Log) -> Boolean): List<Log> {
    return checkNotNull(data.logs).filter { predicate(it) }
}

fun Envelope<LogPayload>.getLogsWithAttributeValue(name: String, value: String): List<Log> =
    getLogs { it.attributes?.findAttributeValue(name) == value }

fun Envelope<LogPayload>.getLogWithAttributeValue(name: String, value: String): Log =
    getLogsWithAttributeValue(name, value).single()

fun Envelope<LogPayload>.getLogsOfType(type: EmbType): List<Log> = getLogsWithAttributeValue("emb.type", type.value)

fun Envelope<LogPayload>.getLogOfType(type: EmbType): Log = getLogsOfType(type).single()
