package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryClock
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.clock.SystemClock
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.SpansService
import io.embrace.android.embracesdk.telemetry.EmbraceTelemetryService
import io.embrace.android.embracesdk.telemetry.TelemetryService

internal class FakeInitModule(
    override val clock: Clock = NormalizedIntervalClock(systemClock = SystemClock()),
    override val telemetryService: TelemetryService = EmbraceTelemetryService(),
    override val spansService: SpansService = EmbraceSpansService(FakeOpenTelemetryClock(clock), telemetryService),
    override val tracer: EmbraceTracer = EmbraceTracer(spansService)
) : InitModule
