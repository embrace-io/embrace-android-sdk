package io.embrace.android.embracesdk.internal.arch.datasource

class SpanEventImpl(
    override val name: String,
    override val timestampNanos: Long,
    override val attributes: Map<String, String>,
) : SpanEvent
