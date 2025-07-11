package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.tracing.model.SpanEvent

@OptIn(ExperimentalApi::class)
internal class EmbSpanEvent(
    override val name: String,
    override val timestamp: Long,
    private val attrs: AttributeContainer,
) : SpanEvent, AttributeContainer by attrs
