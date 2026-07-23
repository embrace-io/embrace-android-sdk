package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import okhttp3.OkHttpClient

internal class TelemetryDestinationHarness {

    private val initModule = TestInitModule()
    private val otelModule = OpenTelemetryModuleImpl(initModule).apply {
        this.spanService.initializeService(0)
    }

    val logSink: LogSink = otelModule.logSink
    val spanSink: SpanSink = otelModule.spanSink
    val spanService = otelModule.spanService
    val currentSessionPartSpan = otelModule.currentSessionPartSpan

    val destination: TelemetryDestination = TelemetryDestinationImpl(
        clock = initModule.clock,
        spanService = spanService,
        eventService = otelModule.eventService,
        currentSessionPartSpan = otelModule.currentSessionPartSpan,
    )

    /**
     * Builds a [SpanService] wired with the module's real tracer, OTel SDK, and span repository,
     * but where no per-session span limit is applied
     */
    fun createUncappedSpanService(): SpanService {
        val validator = DataValidator(telemetryService = NoopTelemetryService)
        val factory = EmbraceSpanFactoryImpl(
            openTelemetryClock = EmbClock(initModule.clock),
            spanRepository = otelModule.spanRepository,
            dataValidator = validator,
            telemetryService = NoopTelemetryService,
        )
        return EmbraceSpanService(
            spanRepository = otelModule.spanRepository,
            dataValidator = validator,
            canStartNewSpan = { _, _ -> true },
            initCallback = {},
            embraceSpanFactorySupplier = { factory },
            tracerSupplier = { otelModule.otelSdkWrapper.sdkTracer },
            openTelemetrySupplier = { otelModule.otelSdkWrapper.openTelemetryKotlin },
        ).apply { initializeService(0) }
    }

    private class TestInitModule : InitModule {
        override val clock: io.embrace.android.embracesdk.internal.clock.Clock = NormalizedIntervalClock()
        override val telemetryService: TelemetryService = NoopTelemetryService
        override val logger: InternalLogger = InternalLoggerImpl()
        override val systemInfo: SystemInfo = SystemInfo()
        override val uuidSource: UuidSource = UuidSourceImpl()
        override val startupClassifier: StartupClassifier = StartupClassifierImpl()
        override val jsonSerializer: PlatformSerializer = EmbraceSerializer()
        override val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl
        override val okHttpClient: Lazy<OkHttpClient> = lazyOf(OkHttpClient())
    }

    private object NoopTelemetryService : TelemetryService {
        override fun onPublicApiCalled(name: String) {
        }

        override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        }

        override fun trackAppliedLimit(telemetryType: String, limitType: AppliedLimitType) {
        }

        override fun getAndClearTelemetryAttributes(): Map<String, String> = emptyMap()
    }
}
