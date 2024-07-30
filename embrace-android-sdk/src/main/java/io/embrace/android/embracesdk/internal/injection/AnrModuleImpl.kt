package io.embrace.android.embracesdk.internal.injection

import android.os.Looper
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.anr.AnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.AnrService
import io.embrace.android.embracesdk.internal.anr.EmbraceAnrService
import io.embrace.android.embracesdk.internal.anr.NoOpAnrService
import io.embrace.android.embracesdk.internal.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.internal.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.internal.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.internal.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.internal.anr.sigquit.AnrThreadIdDelegate
import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class AnrModuleImpl(
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    workerModule: WorkerThreadModule,
    otelModule: OpenTelemetryModule
) : AnrModule {

    private val anrMonitorWorker = workerModule.scheduledWorker(WorkerName.ANR_MONITOR)
    private val configService = essentialServiceModule.configService

    override val anrService: AnrService by singleton {
        if (configService.autoDataCaptureBehavior.isAnrServiceEnabled()) {
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
                anrMonitorThread = workerModule.anrMonitorThread
            )
        } else {
            NoOpAnrService()
        }
    }

    override val anrOtelMapper: AnrOtelMapper by singleton {
        AnrOtelMapper(anrService, initModule.clock)
    }

    override val sigquitDataSource: SigquitDataSource by singleton {
        SigquitDataSource(
            sharedObjectLoader = SharedObjectLoader(logger = initModule.logger),
            anrThreadIdDelegate = AnrThreadIdDelegate(initModule.logger),
            anrBehavior = configService.anrBehavior,
            logger = initModule.logger,
            writer = otelModule.currentSessionSpan
        )
    }

    private val looper by singleton { Looper.getMainLooper() }

    private val state by singleton { ThreadMonitoringState(initModule.clock) }

    private val targetThreadHandler by singleton {
        TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = anrMonitorWorker,
            anrMonitorThread = workerModule.anrMonitorThread,
            configService = configService,
            logger = initModule.logger,
            clock = initModule.clock,
        )
    }

    val blockedThreadDetector by singleton {
        BlockedThreadDetector(
            configService = configService,
            clock = initModule.clock,
            state = state,
            targetThread = looper.thread,
            anrMonitorThread = workerModule.anrMonitorThread,
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
            anrMonitorThread = workerModule.anrMonitorThread,
            logger = initModule.logger,
        )
    }
}
