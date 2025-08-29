package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.config.USE_KOTLIN_SDK
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSinkImpl
import io.embrace.android.embracesdk.internal.spans.CurrentSessionSpan
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.spans.InternalTracer
import io.embrace.opentelemetry.kotlin.Clock
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
class FakeOpenTelemetryModule(
    override val currentSessionSpan: CurrentSessionSpan = FakeCurrentSessionSpan(),
    override val spanSink: SpanSink = SpanSinkImpl(),
    override val logSink: LogSink = LogSinkImpl(),
    override val spanRepository: SpanRepository = SpanRepository(),
    useKotlinSdk: Boolean = USE_KOTLIN_SDK,
) : OpenTelemetryModule {
    private val sdkName = "sdk"
    private val sdkVersion = "1.0"
    private val systemInfo = SystemInfo()

    override val otelSdkConfig: OtelSdkConfig =
        OtelSdkConfig(
            spanSink = spanSink,
            logSink = logSink,
            sdkName = sdkName,
            sdkVersion = sdkVersion,
            systemInfo = systemInfo,
            useKotlinSdk = useKotlinSdk,
        )

    override val openTelemetryClock: Clock = FakeOtelKotlinClock(FakeClock())

    override val spanService: SpanService = FakeSpanService(otelSdkConfig.useKotlinSdk)

    override val otelSdkWrapper: OtelSdkWrapper =
        OtelSdkWrapper(
            otelClock = openTelemetryClock,
            configuration = otelSdkConfig,
            spanService = spanService,
        )

    override val embraceTracer: EmbraceTracer
        get() = TODO()

    override val internalTracer: InternalTracer
        get() = TODO()

    override fun applyConfiguration(sensitiveKeysBehavior: SensitiveKeysBehavior, bypassValidation: Boolean) {
        // no-op
    }
}
