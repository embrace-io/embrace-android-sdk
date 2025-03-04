package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.internal.arch.schema.TelemetryType
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload
import io.embrace.android.embracesdk.internal.spans.findAttributeValue

/**
 * Returns the last log in a list of log payloads.
 */
internal fun List<Envelope<LogPayload>>.getLastLog(): Log {
    return checkNotNull(last().getLastLog())
}

internal fun Envelope<LogPayload>.getLastLog(): Log {
    return checkNotNull(data.logs).last()
}

internal fun Envelope<LogPayload>.getLogs(predicate: (Log) -> Boolean): List<Log> {
    return checkNotNull(data.logs).filter { predicate(it) }
}

internal fun Envelope<LogPayload>.getLog(predicate: (Log) -> Boolean): Log = getLogs { predicate(it) }.single()

internal fun Envelope<LogPayload>.getLogsWithAttributeValue(name: String, value: String): List<Log> =
    getLogs { it.attributes?.findAttributeValue(name) == value }

internal fun Envelope<LogPayload>.getLogWithAttributeValue(name: String, value: String): Log =
    getLogsWithAttributeValue(name, value).single()

internal fun Envelope<LogPayload>.getLogsOfType(type: TelemetryType): List<Log> = getLogsWithAttributeValue("emb.type", type.value)

internal fun Envelope<LogPayload>.getLogOfType(type: TelemetryType): Log = getLogsOfType(type).single()
