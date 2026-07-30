package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.logs.EventService
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.spans.TracingApi

class FakeOpenTelemetryModule(
    override val currentSessionPartSpan: CurrentSessionPartSpan = FakeCurrentSessionPartSpan(),
    override val logSink: LogSink = LogSinkImpl(),
    override val spanRepository: SpanRepository = SpanRepository(),
    useKotlinSdk: Boolean = true,
) : OpenTelemetryModule {
    private val sdkName = "sdk"
    private val sdkVersion = "1.0"
    private val systemInfo = SystemInfo()

    override val otelSdkConfig: OtelSdkConfig =
        OtelSdkConfig(
            spanRepository = spanRepository,
            logSink = logSink,
            sdkName = sdkName,
            sdkVersion = sdkVersion,
            appVersion = "1.0.0",
            packageName = "com.test.app",
            systemInfo = systemInfo,
            uuidSource = TestUuidSource(),
        )

    override val eventService: EventService = FakeEventService()

    override val spanService: SpanService = FakeSpanService()

    override val otelSdkWrapper: OtelSdkWrapper =
        OtelSdkWrapper(
            otelClock = FakeOtelKotlinClock(),
            configuration = otelSdkConfig,
            spanService = spanService,
            useKotlinSdk = useKotlinSdk,
        )

    override val tracingApi: TracingApi
        get() = TODO()

    override fun applyConfiguration(sensitiveKeysBehavior: SensitiveKeysBehavior, bypassValidation: Boolean, otelBehavior: OtelBehavior) {
        // no-op
    }

    override fun setSessionIdsProvider(sessionIdsProvider: SessionIdsProvider) {
        // no-op
    }

    override fun setUserIdProvider(userIdProvider: () -> String?) {
        // no-op
    }

    override fun setEventMetadataProvider(eventMetadataProvider: () -> Map<String, String>) {
        // no-op
    }
}
