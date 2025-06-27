package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.model.LogRecord
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber

@OptIn(ExperimentalApi::class)
class FakeLogRecord(
    override val observedTimestamp: Long?,
    override val severityNumber: SeverityNumber?,
    override val timestamp: Long?,
    override val body: String?,
    override val context: io.embrace.opentelemetry.kotlin.context.Context?,
    override val severityText: String?,
    private val attrs: Map<String, Any>,
) : LogRecord {
    override fun attributes(): Map<String, Any> = attrs
    override fun setBooleanAttribute(key: String, value: Boolean) = throw UnsupportedOperationException()
    override fun setBooleanListAttribute(key: String, value: List<Boolean>) = throw UnsupportedOperationException()
    override fun setDoubleAttribute(key: String, value: Double) = throw UnsupportedOperationException()
    override fun setDoubleListAttribute(key: String, value: List<Double>) = throw UnsupportedOperationException()
    override fun setLongAttribute(key: String, value: Long) = throw UnsupportedOperationException()
    override fun setLongListAttribute(key: String, value: List<Long>) = throw UnsupportedOperationException()
    override fun setStringAttribute(key: String, value: String) = throw UnsupportedOperationException()
    override fun setStringListAttribute(key: String, value: List<String>) = throw UnsupportedOperationException()
}
