package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrOtelMapper
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
fun fakeAnrOtelMapper(): AnrOtelMapper = AnrOtelMapper(
    FakeAnrService(),
    FakeClock(),
    FakeSpanService(),
    FakeOpenTelemetryModule().otelSdkWrapper.openTelemetryKotlin.tracingIdFactory
)
