package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.isAppStateEnabled
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.state.StateExportMode
import io.embrace.android.embracesdk.internal.otel.state.StateReplayEmitter
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.logging.Logger

/**
 * Creates the recorder that holds every state's value and orders every change, with what ties telemetry to it, or returns null
 * when the app state data model is off. Nothing of the model exists then, and state capture is as it was.
 *
 * The recorder is attached to the event bus here, before any data source can declare a state on it. The bus is only asked for when
 * the model is on.
 */
fun createStateTelemetry(
    configService: ConfigService,
    clock: Clock,
    telemetryService: InternalTelemetryService,
    externalOtelConfig: ExternalOtelConfig,
    eventBus: Provider<EventBus>,
): StateTelemetry? {
    if (!configService.isAppStateEnabled()) {
        return null
    }
    val recorder = StateRecorder(clock, telemetryService)
    recorder.attach(eventBus())
    return StateTelemetry(
        recorder = recorder,
        exportMode = {
            StateExportMode.resolve(
                configured = configService.config.appState.exportMode,
                receivesLogs = externalOtelConfig.receivesLogs(),
                receivesSpans = externalOtelConfig.receivesSpans(),
            )
        },
        receivesLogs = externalOtelConfig::receivesLogs,
    )
}

/**
 * Starts replaying state changes to the app's own exporters as events, if the app registered anything that receives log records.
 * An app with only a span exporter gets none, and gets the values on its spans instead.
 */
fun startStateReplay(
    stateTelemetry: StateTelemetry,
    externalOtelConfig: ExternalOtelConfig,
    loggerProvider: Provider<Logger>,
    worker: BackgroundWorker,
    processIdentifier: Provider<String>,
    errorHandler: ErrorHandler,
    telemetryService: InternalTelemetryService,
) {
    if (!externalOtelConfig.receivesLogs()) {
        return
    }
    stateTelemetry.recorder.replay = StateReplayEmitter(
        loggerProvider = loggerProvider,
        worker = worker,
        processIdentifier = processIdentifier,
        stateTelemetry = stateTelemetry,
        errorHandler = errorHandler,
        telemetryService = telemetryService,
    )
}
