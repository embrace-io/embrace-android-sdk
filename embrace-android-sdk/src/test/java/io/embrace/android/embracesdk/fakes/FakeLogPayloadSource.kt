package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.log.LogPayloadSource
import io.embrace.android.embracesdk.internal.payload.LogPayload

internal class FakeLogPayloadSource : LogPayloadSource {

    var logs: LogPayload = LogPayload()

    override fun getLogPayload(): LogPayload = logs
}
