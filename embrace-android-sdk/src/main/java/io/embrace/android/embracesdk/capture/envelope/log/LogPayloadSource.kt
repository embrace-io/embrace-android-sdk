package io.embrace.android.embracesdk.capture.envelope.log

import io.embrace.android.embracesdk.internal.payload.LogPayload

internal interface LogPayloadSource {

    fun getLogPayload(): LogPayload
}
