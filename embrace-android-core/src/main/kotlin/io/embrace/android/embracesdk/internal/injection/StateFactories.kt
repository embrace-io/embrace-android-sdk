package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.isAppStateEnabled
import io.embrace.android.embracesdk.internal.config.isStateAssociationEnabled
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.otel.config.ExternalOtelConfig
import io.embrace.android.embracesdk.internal.otel.state.StateExportMode
import io.embrace.android.embracesdk.internal.otel.state.StateReplayEmitter
import io.embrace.android.embracesdk.internal.otel.state.StateTelemetry
import io.embrace.android.embracesdk.internal.state.StateRecorder
import io.embrace.android.embracesdk.internal.telemetry.AppliedLimitType
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.opentelemetry.kotlin.context.Context
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
    // the model's second step: spans linked to state
    val associationEnabled = configService.isStateAssociationEnabled()
    // only a span given its own times reads the recent snapshots, so they are kept only where spans are linked
    val recorder = StateRecorder(clock, telemetryService, keepsWindow = associationEnabled)
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
        associationEnabled = associationEnabled,
        onPlainStampSkipped = { telemetryService.trackAppliedLimit(PLAIN_STAMP_TELEMETRY_NAME, AppliedLimitType.DROP) },
        receivesSpans = externalOtelConfig::receivesSpans,
    )
}

/** The name a log that could not be given the stamp for the app's own exporters is tracked under. */
private const val PLAIN_STAMP_TELEMETRY_NAME = "state_log_stamp"

/**
 * Starts replaying state changes to the app's own exporters as events, if the app registered anything that receives log records.
 * It is part of the model's first step: the events are what such an app gets in place of the state spans it is exported with the
 * model off. An app with only a span exporter gets none, and gets the values on its spans instead, which takes the second step.
 *
 * [worker] is asked for only if the replay starts, and is used for nothing else: an app's own log record processor runs on it
 * for as long as it takes.
 */
fun startStateReplay(
    stateTelemetry: StateTelemetry,
    externalOtelConfig: ExternalOtelConfig,
    loggerProvider: Provider<Logger>,
    worker: Provider<BackgroundWorker>,
    processIdentifier: Provider<String>,
    errorHandler: ErrorHandler,
    telemetryService: InternalTelemetryService,
    rootContextProvider: Provider<Context>,
) {
    if (!externalOtelConfig.receivesLogs()) {
        return
    }
    stateTelemetry.recorder.attach(
        StateReplayEmitter(
            loggerProvider = loggerProvider,
            worker = worker(),
            processIdentifier = processIdentifier,
            stateTelemetry = stateTelemetry,
            errorHandler = errorHandler,
            telemetryService = telemetryService,
            rootContextProvider = rootContextProvider,
        ),
    )
}
