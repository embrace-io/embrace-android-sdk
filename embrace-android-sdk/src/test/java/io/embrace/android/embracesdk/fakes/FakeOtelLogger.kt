package io.embrace.android.embracesdk.fakes

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.TimeUnit

internal class FakeOtelLogger : Logger {
    override fun logRecordBuilder(): LogRecordBuilder = FakeLogRecordBuilder()
}

internal class FakeLogRecordBuilder : LogRecordBuilder {
    override fun setTimestamp(timestamp: Long, unit: TimeUnit): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun setTimestamp(instant: Instant): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun setObservedTimestamp(timestamp: Long, unit: TimeUnit): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun setObservedTimestamp(instant: Instant): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun setContext(context: Context): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun setSeverity(severity: Severity): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun setSeverityText(severityText: String): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun setBody(body: String): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun <T : Any> setAttribute(key: AttributeKey<T>, value: T): LogRecordBuilder {
        TODO("Not yet implemented")
    }

    override fun emit() {
        TODO("Not yet implemented")
    }
}
