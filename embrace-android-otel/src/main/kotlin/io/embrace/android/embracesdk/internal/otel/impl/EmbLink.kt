package io.embrace.android.embracesdk.internal.otel.impl

import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.attributes.AttributeContainer
import io.embrace.opentelemetry.kotlin.tracing.model.Link
import io.embrace.opentelemetry.kotlin.tracing.model.SpanContext

@OptIn(ExperimentalApi::class)
internal class EmbLink(
    override val spanContext: SpanContext,
    private val attrs: AttributeContainer,
) : Link, AttributeContainer by attrs
