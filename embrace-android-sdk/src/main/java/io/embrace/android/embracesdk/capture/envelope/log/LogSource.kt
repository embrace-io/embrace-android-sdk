package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.internal.payload.Log

internal interface LogSource {
    fun getLogPayload(): Log
}
