package io.embrace.android.embracesdk.testframework.assertions

import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.Log
import io.embrace.android.embracesdk.internal.payload.LogPayload

/**
 * Returns the last log in a list of log payloads.
 */
internal fun List<Envelope<LogPayload>>.getLastLog(): Log {
    return checkNotNull(last().getLastLog())
}

internal fun Envelope<LogPayload>.getLastLog(): Log {
    return checkNotNull(data.logs).last()
}
