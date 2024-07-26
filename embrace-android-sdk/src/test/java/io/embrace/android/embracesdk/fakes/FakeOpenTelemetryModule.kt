package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.opentelemetry.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.opentelemetry.OpenTelemetryConfiguration
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.mockk.mockk
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.common.Clock

internal class FakeOpenTelemetryModule(
    override val currentSessionSpan: CurrentSessionSpan = FakeCurrentSessionSpan(),
    override val spanSink: SpanSink = SpanSinkImpl(),
    override val logSink: LogSink = LogSinkImpl(),
    override val spanRepository: SpanRepository = SpanRepository(),
) : OpenTelemetryModule {
    override val openTelemetryConfiguration: OpenTelemetryConfiguration = mockk(relaxed = true)
    override val sdkTracer: Tracer
        get() = FakeTracer()
    override val spanService: SpanService
        get() = FakeSpanService()
    override val embraceTracer: EmbraceTracer
        get() = TODO()
    override val internalTracer: InternalTracer
        get() = TODO()
    override val logger: Logger
        get() = FakeOtelLogger()
    override val externalOpenTelemetry: OpenTelemetry
        get() = EmbOpenTelemetry(traceProviderSupplier = { FakeTracerProvider() })
    override val externalTracerProvider: TracerProvider
        get() = FakeTracerProvider()
    override val openTelemetryClock: Clock
        get() = FakeOpenTelemetryClock(FakeClock())
}
