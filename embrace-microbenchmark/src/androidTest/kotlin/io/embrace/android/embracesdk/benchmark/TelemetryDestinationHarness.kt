package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.logging.InternalLoggerImpl
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.opentelemetry.kotlin.ExperimentalApi
import okhttp3.OkHttpClient

@OptIn(ExperimentalApi::class)
internal class TelemetryDestinationHarness {

    private val initModule = TestInitModule()
    private val otelModule = OpenTelemetryModuleImpl(initModule).apply {
        this.spanService.initializeService(0)
    }

    val logSink: LogSink = otelModule.logSink
    val spanSink: SpanSink = otelModule.spanSink
    val spanService = otelModule.spanService
    val currentSessionSpan = otelModule.currentSessionSpan

    val destination: TelemetryDestination = TelemetryDestinationImpl(
        clock = initModule.clock,
        spanService = spanService,
        eventService = otelModule.eventService,
        currentSessionSpan = otelModule.currentSessionSpan,
    )

    private class TestInitModule : InitModule {
        override val clock: io.embrace.android.embracesdk.internal.clock.Clock = NormalizedIntervalClock()
        override val telemetryService: TelemetryService = NoopTelemetryService
        override val logger: InternalLogger = InternalLoggerImpl()
        override val systemInfo: SystemInfo = SystemInfo()
        override val jsonSerializer: PlatformSerializer = EmbraceSerializer()
        override val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl
        override val okHttpClient: OkHttpClient = OkHttpClient()
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
