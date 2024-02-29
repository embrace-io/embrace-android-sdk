package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger

internal class FakeOpenTelemetryLogger : Logger {

    val builders = mutableListOf<FakeLogRecordBuilder>()

    override fun logRecordBuilder(): LogRecordBuilder {
        val builder = FakeLogRecordBuilder()
        builders.add(builder)
        return builder
    }
}
