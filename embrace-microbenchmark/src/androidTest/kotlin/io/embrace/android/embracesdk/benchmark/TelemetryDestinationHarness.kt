package io.embrace.android.embracesdk.benchmark

import io.embrace.android.embracesdk.internal.SystemInfo
import io.embrace.android.embracesdk.internal.arch.SessionChangeListener
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.arch.state.AppStateListener
import io.embrace.android.embracesdk.internal.arch.state.AppStateTracker
import io.embrace.android.embracesdk.internal.clock.NormalizedIntervalClock
import io.embrace.android.embracesdk.internal.config.instrumented.InstrumentedConfigImpl
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModuleImpl
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.EmbLoggerImpl
import io.embrace.android.embracesdk.internal.otel.logs.LogSink
import io.embrace.android.embracesdk.internal.otel.spans.SpanSink
import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.SessionZygote
import io.embrace.android.embracesdk.internal.session.id.SessionData
import io.embrace.android.embracesdk.internal.session.id.SessionTracker
import io.embrace.android.embracesdk.internal.telemetry.TelemetryService
import io.embrace.opentelemetry.kotlin.ExperimentalApi

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
        logger = otelModule.otelSdkWrapper.logger,
        sessionTracker = NoopSessionTracker,
        appStateTracker = NoopAppStateTracker,
        clock = initModule.clock,
        spanService = spanService,
        currentSessionSpan = otelModule.currentSessionSpan,
    )

    private class TestInitModule : InitModule {
        override val clock: io.embrace.android.embracesdk.internal.clock.Clock = NormalizedIntervalClock()
        override val telemetryService: TelemetryService = NoopTelemetryService
        override val logger: EmbLogger = EmbLoggerImpl()
        override val systemInfo: SystemInfo = SystemInfo()
        override val jsonSerializer: PlatformSerializer = EmbraceSerializer()
        override val instrumentedConfig: InstrumentedConfig = InstrumentedConfigImpl
    }

    private object NoopTelemetryService : TelemetryService {
        override fun onPublicApiCalled(name: String) {
        }

        override fun logStorageTelemetry(storageTelemetry: Map<String, String>) {
        }

        override fun getAndClearTelemetryAttributes(): Map<String, String> = emptyMap()
    }

    private object NoopSessionTracker : SessionTracker {
        override fun getActiveSession(): SessionData? = null
        override fun newActiveSession(
            endingSession: SessionZygote?,
            endSessionCallback: SessionZygote.() -> Unit,
            startSessionCallback: () -> SessionZygote?,
            appState: AppState,
        ): SessionZygote? = null

        override fun addListener(listener: SessionChangeListener) {
        }
    }

    private object NoopAppStateTracker : AppStateTracker {
        override fun addListener(listener: AppStateListener) {
        }

        override fun getAppState(): AppState = AppState.FOREGROUND
    }
}
