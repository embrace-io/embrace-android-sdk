package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger

class FakeOpenTelemetryLogger : Logger {

    val builders: MutableList<FakeLogRecordBuilder> = mutableListOf()

    override fun logRecordBuilder(): LogRecordBuilder {
        val builder = FakeLogRecordBuilder()
        builders.add(builder)
        return builder
    }
}
