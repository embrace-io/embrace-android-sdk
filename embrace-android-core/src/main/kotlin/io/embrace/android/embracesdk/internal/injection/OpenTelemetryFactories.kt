package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.config.behavior.OtelBehavior
import io.embrace.android.embracesdk.internal.config.behavior.REDACTED_LABEL
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.config.OtelSdkConfig
import io.embrace.android.embracesdk.internal.otel.export.ExternalExportDispatcher
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.IdGenerator
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.spans.CurrentSessionPartSpanImpl
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer

fun createOtelSdkConfig(
    spanRepository: SpanRepository,
    logSink: LogSink,
    instrumentedConfig: InstrumentedConfig,
    systemInfo: SystemInfo,
    uuidSource: UuidSource,
    errorHandler: ErrorHandler,
    externalOtelConfig: ExternalOtelConfig = ExternalOtelConfig(),
    sessionIdsProvider: () -> SessionIdsProvider? = { null },
    userIdProvider: () -> String? = { null },
    eventMetadataProvider: () -> Map<String, String> = { emptyMap() },
): OtelSdkConfig = OtelSdkConfig(
    spanRepository = spanRepository,
    logSink = logSink,
    sdkName = BuildConfig.LIBRARY_PACKAGE_NAME,
    sdkVersion = BuildConfig.VERSION_NAME,
    appVersion = instrumentedConfig.project.getVersionName() ?: "UNKNOWN",
    packageName = instrumentedConfig.project.getPackageName() ?: "UNKNOWN",
    systemInfo = systemInfo,
    uuidSource = uuidSource,
    sessionIdsProvider = sessionIdsProvider,
    userIdProvider = userIdProvider,
    eventMetadataProvider = eventMetadataProvider,
    processIdentifierProvider = IdGenerator.Companion::generateLaunchInstanceId,
    externalExportDispatcher = ExternalExportDispatcher(errorHandler),
    externalConfig = externalOtelConfig,
)

fun createCurrentSessionPartSpan(
    openTelemetryClock: Clock,
    telemetryService: InternalTelemetryService,
    spanRepository: SpanRepository,
    tracerSupplier: () -> Tracer,
    openTelemetrySupplier: () -> OpenTelemetry,
    embraceSpanFactorySupplier: () -> EmbraceSpanFactory,
    uuidSource: UuidSource,
    otelBehaviorSupplier: () -> OtelBehavior?,
    customBreadcrumbLimitSupplier: () -> Int,
): CurrentSessionPartSpan = CurrentSessionPartSpanImpl(
    openTelemetryClock = openTelemetryClock,
    telemetryService = telemetryService,
    spanRepository = spanRepository,
    tracerSupplier = tracerSupplier,
    openTelemetrySupplier = openTelemetrySupplier,
    embraceSpanFactorySupplier = embraceSpanFactorySupplier,
    uuidSource = uuidSource,
    otelBehaviorSupplier = otelBehaviorSupplier,
    customBreadcrumbLimitSupplier = customBreadcrumbLimitSupplier,
)

fun createRedactionFunction(sensitiveKeysBehavior: SensitiveKeysBehavior): (key: String, value: String) -> String =
    { key, value ->
        if (sensitiveKeysBehavior.isSensitiveKey(key)) {
            REDACTED_LABEL
        } else {
            value
        }
    }
