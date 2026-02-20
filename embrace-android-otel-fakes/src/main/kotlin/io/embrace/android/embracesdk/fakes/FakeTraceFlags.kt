package io.embrace.android.embracesdk.fakes

import io.opentelemetry.kotlin.ExperimentalApi
import io.opentelemetry.kotlin.tracing.model.TraceFlags

@ExperimentalApi
class FakeTraceFlags(
    val hex: String = "00",
    override val isRandom: Boolean = true,
    override val isSampled: Boolean = false,
) : TraceFlags
