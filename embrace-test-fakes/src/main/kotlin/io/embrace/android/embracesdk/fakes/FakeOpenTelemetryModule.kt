package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan

class FakeOpenTelemetryModule(
    val currentSessionPartSpan: CurrentSessionPartSpan = FakeCurrentSessionPartSpan(),
    val logSink: LogSink = LogSinkImpl(),
    val spanRepository: SpanRepository = SpanRepository(),
    otelSdkMode: OtelSdkMode,
) {
    private val sdkName = "sdk"
    private val sdkVersion = "1.0"
    private val systemInfo = SystemInfo()

    val otelSdkConfig: OtelSdkConfig =
        OtelSdkConfig(
            spanRepository = spanRepository,
            logSink = logSink,
            sdkName = sdkName,
            sdkVersion = sdkVersion,
            appVersion = "1.0.0",
            packageName = "com.test.app",
            systemInfo = systemInfo,
            uuidSource = TestUuidSource(),
            externalExportDispatcher = ExternalExportDispatcher(FakeErrorHandler()),
        )

    val spanService: SpanService = FakeSpanService()

    val otelSdkWrapper: OtelSdkWrapper =
        OtelSdkWrapper(
            otelClock = FakeOtelKotlinClock(),
            configuration = otelSdkConfig,
            spanServiceProvider = { spanService },
            errorHandler = FakeErrorHandler(),
            useKotlinSdk = otelSdkMode.useKotlinSdk,
        )
}
