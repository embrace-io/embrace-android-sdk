package io.embrace.android.embracesdk.internal.otel.impl

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.tracing.model.SpanEvent

@OptIn(ExperimentalApi::class)
internal class EmbSpanEvent(
    override val name: String,
    override val timestamp: Long,
    private val attrs: MutableAttributeContainer,
) : SpanEvent, MutableAttributeContainer by attrs
