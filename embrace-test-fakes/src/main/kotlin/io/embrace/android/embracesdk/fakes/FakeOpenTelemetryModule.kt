@file:OptIn(ExperimentalApi::class)

package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.impl.EmbOpenTelemetry
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.opentelemetry.kotlin.ExperimentalApi
import io.embrace.opentelemetry.kotlin.logging.Logger
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.sdk.common.Clock

class FakeOpenTelemetryModule(
    override val currentSessionSpan: CurrentSessionSpan = FakeCurrentSessionSpan(),
    override val spanSink: SpanSink = SpanSinkImpl(),
    override val logSink: LogSink = LogSinkImpl(),
    override val spanRepository: SpanRepository = SpanRepository(),
) : OpenTelemetryModule {
    override val otelSdkConfig: OtelSdkConfig =
        OtelSdkConfig(spanSink, logSink, "sdk", "1.0", SystemInfo())

    override fun applyConfiguration(sensitiveKeysBehavior: SensitiveKeysBehavior, bypassValidation: Boolean) {
        // no-op
    }

    override val sdkTracer: io.embrace.opentelemetry.kotlin.tracing.Tracer
        get() = FakeKotlinTracer()
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
