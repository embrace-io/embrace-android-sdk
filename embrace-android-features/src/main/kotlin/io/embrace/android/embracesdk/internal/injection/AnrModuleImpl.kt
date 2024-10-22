package io.embrace.android.embracesdk.internal.injection

import android.os.Looper
import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.anr.EmbraceAnrService
import io.embrace.android.embracesdk.internal.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.worker.Worker

internal class AnrModuleImpl(
    initModule: InitModule,
    configService: ConfigService,
    workerModule: WorkerThreadModule
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
                clock = initModule.clock
            )
        } else {
            null
        }
    }

    override val anrOtelMapper: AnrOtelMapper? by singleton {
        if (configService.autoDataCaptureBehavior.isAnrCaptureEnabled()) {
            AnrOtelMapper(checkNotNull(anrService), initModule.clock)
        } else {
            null
        }
    }

    private val looper by singleton { Looper.getMainLooper() }

    private val state by singleton { ThreadMonitoringState(initModule.clock) }

    private val targetThreadHandler by singleton {
        TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = anrMonitorWorker,
            configService = configService,
            logger = initModule.logger,
            clock = initModule.clock,
        )
    }

    override val blockedThreadDetector by singleton {
        BlockedThreadDetector(
            configService = configService,
            clock = initModule.clock,
            state = state,
            targetThread = looper.thread,
            logger = initModule.logger,
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
