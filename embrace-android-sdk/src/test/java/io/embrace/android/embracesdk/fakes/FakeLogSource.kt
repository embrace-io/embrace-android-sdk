package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.envelope.log.LogSource
import io.embrace.android.embracesdk.internal.payload.Log

internal class FakeLogSource : LogSource {

    var log: Log = Log()

    override fun getLogPayload(): Log = log
}
