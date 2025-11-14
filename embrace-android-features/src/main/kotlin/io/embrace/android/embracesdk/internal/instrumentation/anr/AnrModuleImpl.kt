package io.embrace.android.embracesdk.internal.instrumentation.anr

import android.os.Looper
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.session.lifecycle.AppStateService
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.opentelemetry.kotlin.ExperimentalApi

@OptIn(ExperimentalApi::class)
internal class AnrModuleImpl(
    instrumentationModule: InstrumentationModule,
    openTelemetryModule: OpenTelemetryModule,
    appStateService: AppStateService,
) : AnrModule {

    private val args by singleton { instrumentationModule.instrumentationArgs }

    private val anrMonitorWorker = args.backgroundWorker(Worker.Background.AnrWatchdogWorker)

    override val anrService: AnrService? by singleton {
        if (args.configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            EmbraceAnrService(
                configService = args.configService,
                looper = looper,
                logger = args.logger,
                livenessCheckScheduler = livenessCheckScheduler,
                anrMonitorWorker = anrMonitorWorker,
                state = state,
                clock = args.clock,
                stacktraceSampler = stacktraceSampler,
                appStateService = appStateService,
            )
        } else {
            null
        }
    }

    override val anrOtelMapper: AnrOtelMapper? by singleton {
        if (args.configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            AnrOtelMapper(
                checkNotNull(anrService),
                args.clock,
                openTelemetryModule.spanService
            )
        } else {
            null
        }
    }

    private val looper by singleton { Looper.getMainLooper() }

    private val state by singleton { ThreadMonitoringState(args.clock) }

    private val stacktraceSampler by singleton {
        AnrStacktraceSampler(
            configService = args.configService,
            clock = args.clock,
            targetThread = looper.thread,
            anrMonitorWorker = anrMonitorWorker
        )
    }

    private val targetThreadHandler by singleton {
        TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = anrMonitorWorker,
            clock = args.clock,
        )
    }

    override val blockedThreadDetector by singleton {
        BlockedThreadDetector(
            configService = args.configService,
            clock = args.clock,
            state = state,
            targetThread = looper.thread,
        )
    }

    private val livenessCheckScheduler by singleton {
        LivenessCheckScheduler(
            configService = args.configService,
            anrMonitorWorker = anrMonitorWorker,
            clock = args.clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            logger = args.logger,
        )
    }
}
