package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.tracing.model.TraceFlags

class FakeTraceFlags(
    override val isRandom: Boolean = true,
    override val isSampled: Boolean = false,
) : TraceFlags
