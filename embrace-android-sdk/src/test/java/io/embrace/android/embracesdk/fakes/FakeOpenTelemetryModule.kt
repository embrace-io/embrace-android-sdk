package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.arch.destination.LogWriter
import io.embrace.android.embracesdk.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.logs.LogSink
import io.embrace.android.embracesdk.internal.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.android.embracesdk.internal.spans.SpanRepository
import io.embrace.android.embracesdk.internal.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.SpanSink
import io.embrace.android.embracesdk.internal.spans.SpanSinkImpl
import io.embrace.android.embracesdk.opentelemetry.OpenTelemetryConfiguration
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.api.trace.Tracer

internal class FakeOpenTelemetryModule(
    override val currentSessionSpan: CurrentSessionSpan = FakeCurrentSessionSpan(),
    override val spanSink: SpanSink = SpanSinkImpl(),
    override val logSink: LogSink = LogSinkImpl(),
) : OpenTelemetryModule {
    override val openTelemetryConfiguration: OpenTelemetryConfiguration
        get() = TODO()
    override val spanRepository: SpanRepository
        get() = TODO()
    override val tracer: Tracer
        get() = TODO()
    override val spanService: SpanService
        get() = TODO()
    override val embraceTracer: EmbraceTracer
        get() = TODO()
    override val internalTracer: InternalTracer
        get() = TODO()
    override val logWriter: LogWriter
        get() = TODO()
    override val logger: Logger
        get() = TODO()
}
