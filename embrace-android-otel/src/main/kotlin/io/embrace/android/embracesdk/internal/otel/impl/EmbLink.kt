package io.embrace.android.embracesdk.internal.otel.impl

import io.opentelemetry.kotlin.attributes.MutableAttributeContainer
import io.opentelemetry.kotlin.tracing.model.Link
import io.opentelemetry.kotlin.tracing.model.SpanContext

internal class EmbLink(
    override val spanContext: SpanContext,
    private val attrs: MutableAttributeContainer,
) : Link, MutableAttributeContainer by attrs
