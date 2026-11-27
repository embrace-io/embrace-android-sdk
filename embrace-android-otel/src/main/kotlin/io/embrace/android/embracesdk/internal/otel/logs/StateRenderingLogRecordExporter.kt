package io.embrace.android.embracesdk.internal.otel.logs

import io.embrace.android.embracesdk.internal.arch.schema.StateKeys
import io.embrace.android.embracesdk.internal.state.StatePacking
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordExporter

/**
 * Marks a log record that is meant for a customer's exporters alone, such as a state change replayed as an event.
 * Embrace's own payload never holds it, and the marker is removed before a customer's exporter sees the record.
 */
const val EXPORT_ONLY_KEY: String = "emb.export_only"

/**
 * Wraps a [LogRecordExporter] supplied by the SDK user so that it gets state in the form a customer's pipeline can use:
 * a value for each state, `emb.app.state.value.<name>`, in place of the one packed attribute Embrace's own payload
 * carries, and no marker on a record meant for it alone.
 */
internal class StateRenderingLogRecordExporter(
    private val delegate: LogRecordExporter,
) : LogRecordExporter by delegate {

    override suspend fun export(telemetry: List<LogRecordData>): OperationResultCode =
        delegate.export(telemetry.map(::render))

    private fun render(log: LogRecordData): LogRecordData {
        val attributes = log.attributes
        val packed = attributes[StateKeys.PACKED] as? String
        if (packed == null && !attributes.containsKey(EXPORT_ONLY_KEY)) {
            return log
        }
        val rendered = LinkedHashMap<String, Any>(attributes.size + if (packed != null) VALUES_HINT else 0)
        attributes.forEach { (key, value) ->
            if (key != StateKeys.PACKED && key != EXPORT_ONLY_KEY) {
                rendered[key] = value
            }
        }
        if (packed != null) {
            StatePacking.unpack(packed) { name, value, isSystem ->
                rendered[StateKeys.value(name)] = value
                if (isSystem) {
                    rendered[StateKeys.valueType(name)] = StateKeys.SYSTEM
                }
            }
        }
        return RenderedLogRecordData(log, rendered)
    }

    private class RenderedLogRecordData(
        delegate: LogRecordData,
        override val attributes: Map<String, Any>,
    ) : LogRecordData by delegate

    private companion object {
        private const val VALUES_HINT = 8
    }
}
