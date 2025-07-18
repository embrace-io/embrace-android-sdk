package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.InstrumentationScopeInfo
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.context.Context
import io.embrace.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.embrace.opentelemetry.kotlin.logging.model.SeverityNumber
import io.embrace.opentelemetry.kotlin.resource.Resource
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext

@OptIn(ExperimentalApi::class)
class FakeReadWriteLogRecord(
    private val attributeContainer: AttributeContainer = FakeAttributeContainer(),
    override var body: String? = null,
    override val context: Context? = null,
    override val instrumentationScopeInfo: InstrumentationScopeInfo? = null,
    override var observedTimestamp: Long? = null,
    override val resource: Resource? = null,
    override var severityNumber: SeverityNumber? = null,
    override var severityText: String? = null,
    override var timestamp: Long? = null,
    override var spanContext: SpanContext = FakeSpanContext(),
    override val attributes: Map<String, Any> = attributeContainer.attributes(),
) : ReadWriteLogRecord, AttributeContainer by attributeContainer
