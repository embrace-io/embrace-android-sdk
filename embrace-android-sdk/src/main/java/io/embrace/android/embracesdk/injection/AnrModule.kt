package io.embrace.android.embracesdk.injection

import android.os.Looper
import io.embrace.android.embracesdk.anr.AnrService
import io.embrace.android.embracesdk.anr.EmbraceAnrService
import io.embrace.android.embracesdk.anr.NoOpAnrService
import io.embrace.android.embracesdk.anr.detection.AnrProcessErrorSampler
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
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicReference

internal interface AnrModule {
    val googleAnrTimestampRepository: GoogleAnrTimestampRepository
    val anrService: AnrService
}

internal class AnrModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    systemServiceModule: SystemServiceModule,
    essentialServiceModule: EssentialServiceModule
) : AnrModule {

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
                anrExecutorService = anrExecutorService,
                state = state,
                anrProcessErrorSampler = anrProcessErrorSampler,
                clock = initModule.clock,
                anrMonitorThread = anrMonitorThread
            )
        } else {
            NoOpAnrService()
        }
    }

    private val looper by singleton { Looper.getMainLooper() }

    private val state by singleton { ThreadMonitoringState(initModule.clock) }

    private val targetThreadHandler by singleton {
        TargetThreadHandler(
            looper = looper,
            anrExecutorService = anrExecutorService,
            anrMonitorThread = anrMonitorThread,
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
            anrMonitorThread = anrMonitorThread
        )
    }

    private val livenessCheckScheduler by singleton {
        LivenessCheckScheduler(
            configService = configService,
            anrExecutor = anrExecutorService,
            clock = initModule.clock,
            state = state,
            targetThreadHandler = targetThreadHandler,
            blockedThreadDetector = blockedThreadDetector,
            anrMonitorThread = anrMonitorThread
        )
    }

    private val anrProcessErrorSampler by singleton {
        AnrProcessErrorSampler(
            activityManager = systemServiceModule.activityManager,
            configService = configService,
            anrExecutor = anrExecutorService,
            clock = initModule.clock,
            logger = coreModule.logger
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

    private val anrMonitorThreadFactory = ThreadFactory { runnable: Runnable ->
        Executors.defaultThreadFactory().newThread(runnable).apply {
            anrMonitorThread.set(this)
            name = "emb-anr-monitor"
        }
    }

    private val anrExecutorService: ScheduledExecutorService by lazy {
        // must only have one thread in executor pool - synchronization model relies on this fact.
        Executors.newSingleThreadScheduledExecutor(
            anrMonitorThreadFactory
        )
    }

    private val anrMonitorThread = AtomicReference<Thread>()
}
