package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.InstrumentationScopeInfo
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.logging.model.SeverityNumber
import io.opentelemetry.kotlin.resource.Resource
import io.opentelemetry.kotlin.tracing.model.SpanContext

@OptIn(ExperimentalApi::class)
class FakeReadWriteLogRecord(
    private val attributeContainer: MutableAttributeContainer = FakeMutableAttributeContainer(),
    override var body: String? = null,
    override var eventName: String? = null,
    override val instrumentationScopeInfo: InstrumentationScopeInfo = FakeInstrumentationScopeInfo(),
    override var observedTimestamp: Long? = null,
    override val resource: Resource = FakeResource(),
    override var severityNumber: SeverityNumber? = null,
    override var severityText: String? = null,
    override var timestamp: Long? = null,
    override var spanContext: SpanContext = FakeSpanContext(),
    override val attributes: Map<String, Any> = attributeContainer.attributes,
) : ReadWriteLogRecord, MutableAttributeContainer by attributeContainer
