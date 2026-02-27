package io.embrace.android.embracesdk.fakes

import io.embrace.opentelemetry.kotlin.tracing.model.TraceFlags

class FakeTraceFlags(
    override val hex: String = "00",
    override val isRandom: Boolean = true,
    override val isSampled: Boolean = false,
) : TraceFlags
