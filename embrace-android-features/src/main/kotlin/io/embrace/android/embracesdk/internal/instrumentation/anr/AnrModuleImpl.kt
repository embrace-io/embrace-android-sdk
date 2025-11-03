package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.os.Looper
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.session.lifecycle.ProcessStateService
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
internal class AnrModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    workerModule: WorkerThreadModule,
    processStateService: ProcessStateService,
) : AnrModule {

    private val anrMonitorWorker = workerModule.backgroundWorker(Worker.Background.AnrWatchdogWorker)

    override val anrService: AnrService? by singleton {
        if (configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            // the customer didn't enable early ANR detection, so construct the service
            // as part of normal initialization.
            EmbraceAnrService(
                configService = configService,
                looper = looper,
                logger = initModule.logger,
                livenessCheckScheduler = livenessCheckScheduler,
                anrMonitorWorker = anrMonitorWorker,
                state = state,
                clock = initModule.clock,
                stacktraceSampler = stacktraceSampler,
                processStateService = processStateService,
            )
        } else {
            null
        }
    }

    override val anrOtelMapper: AnrOtelMapper? by singleton {
        if (configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            AnrOtelMapper(
                checkNotNull(anrService),
                initModule.clock,
                openTelemetryModule.spanService,
                openTelemetryModule.otelSdkWrapper.openTelemetryKotlin.tracingIdFactory
            )
        } else {
            null
        }
    }

    private val looper by singleton { Looper.getMainLooper() }

    private val state by singleton { ThreadMonitoringState(initModule.clock) }

    private val stacktraceSampler by singleton {
        AnrStacktraceSampler(
            configService = configService,
            clock = initModule.clock,
            targetThread = looper.thread,
            anrMonitorWorker = anrMonitorWorker
        )
    }

    private val targetThreadHandler by singleton {
        TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = anrMonitorWorker,
            clock = initModule.clock,
        )
    }

    override val blockedThreadDetector by singleton {
        BlockedThreadDetector(
            configService = configService,
            clock = initModule.clock,
            state = state,
            targetThread = looper.thread,
        )
    }

    private val livenessCheckScheduler by singleton {
        LivenessCheckScheduler(
            configService = configService,
            anrMonitorWorker = anrMonitorWorker,
            clock = initModule.clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            logger = initModule.logger,
        )
    }
}
