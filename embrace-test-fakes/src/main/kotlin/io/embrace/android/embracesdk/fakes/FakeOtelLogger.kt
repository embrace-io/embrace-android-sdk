package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger

class FakeOtelLogger : Logger {
    override fun logRecordBuilder(): LogRecordBuilder = FakeLogRecordBuilder()
}
