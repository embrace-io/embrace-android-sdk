package io.embrace.android.embracesdk.injection

import android.os.Looper
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.EmbraceAnrService
import io.embrace.android.embracesdk.anr.NoOpAnrService
import io.embrace.android.embracesdk.anr.detection.BlockedThreadDetector
import io.embrace.android.embracesdk.anr.detection.LivenessCheckScheduler
import io.embrace.android.embracesdk.anr.detection.TargetThreadHandler
import io.embrace.android.embracesdk.anr.detection.ThreadMonitoringState
import io.embrace.android.embracesdk.anr.sigquit.FilesDelegate
import io.embrace.android.embracesdk.anr.sigquit.FindGoogleThread
import io.embrace.android.embracesdk.anr.sigquit.GetThreadCommand
import io.embrace.android.embracesdk.anr.sigquit.GetThreadsInCurrentProcess
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrHandlerNativeDelegate
import io.embrace.android.embracesdk.anr.sigquit.GoogleAnrTimestampRepository
import io.embrace.android.embracesdk.anr.sigquit.SigquitDetectionService
import io.embrace.android.embracesdk.capture.monitor.EmbraceResponsivenessMonitorService
import io.embrace.android.embracesdk.capture.monitor.NoOpResponsivenessMonitorService
import io.embrace.android.embracesdk.capture.monitor.ResponsivenessMonitorService
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface AnrModule {
    val googleAnrTimestampRepository: GoogleAnrTimestampRepository
    val anrService: AnrService
    val responsivenessMonitorService: ResponsivenessMonitorService
}

internal class AnrModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    workerModule: WorkerThreadModule,
) : AnrModule {

    private val anrMonitorWorker = workerModule.scheduledWorker(WorkerName.ANR_MONITOR)
    private val configService = essentialServiceModule.configService

    override val googleAnrTimestampRepository: GoogleAnrTimestampRepository by singleton {
        GoogleAnrTimestampRepository(coreModule.logger)
    }

    override val anrService: AnrService by singleton {
        if (configService.autoDataCaptureBehavior.isAnrServiceEnabled() && !ApkToolsConfig.IS_ANR_MONITORING_DISABLED) {
            // the customer didn't enable early ANR detection, so construct the service
            // as part of normal initialization.
            EmbraceAnrService(
                configService = configService,
                looper = looper,
                logger = coreModule.logger,
                sigquitDetectionService = sigquitDetectionService,
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

    override val responsivenessMonitorService: ResponsivenessMonitorService by singleton {
        if (configService.autoDataCaptureBehavior.isAnrServiceEnabled() && !ApkToolsConfig.IS_ANR_MONITORING_DISABLED) {
            EmbraceResponsivenessMonitorService(
                livenessCheckScheduler = livenessCheckScheduler
            )
        } else {
            NoOpResponsivenessMonitorService()
        }
    }

    private val looper by singleton { Looper.getMainLooper() }

    private val state by singleton { ThreadMonitoringState(initModule.clock) }

    private val targetThreadHandler by singleton {
        TargetThreadHandler(
            looper = looper,
            anrMonitorWorker = anrMonitorWorker,
            anrMonitorThread = workerModule.anrMonitorThread,
            configService = configService,
            clock = initModule.clock
        )
    }

    private val blockedThreadDetector by singleton {
        BlockedThreadDetector(
            configService = configService,
            clock = initModule.clock,
            state = state,
            targetThread = looper.thread,
            anrMonitorThread = workerModule.anrMonitorThread
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
            anrMonitorThread = workerModule.anrMonitorThread
        )
    }

    private val sigquitDetectionService: SigquitDetectionService by singleton {
        val filesDelegate = FilesDelegate()

        SigquitDetectionService(
            sharedObjectLoader = SharedObjectLoader(),
            findGoogleThread = FindGoogleThread(
                coreModule.logger,
                GetThreadsInCurrentProcess(filesDelegate),
                GetThreadCommand(filesDelegate)
            ),
            googleAnrHandlerNativeDelegate = GoogleAnrHandlerNativeDelegate(googleAnrTimestampRepository, coreModule.logger),
            googleAnrTimestampRepository = googleAnrTimestampRepository,
            configService = configService,
            logger = coreModule.logger
        )
    }
}
