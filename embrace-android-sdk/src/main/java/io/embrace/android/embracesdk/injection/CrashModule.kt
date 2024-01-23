package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.crash.CrashService
import io.embrace.android.embracesdk.capture.crash.EmbraceCrashService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.samples.AutomaticVerificationExceptionHandler

/**
 * Contains dependencies that capture crashes
 */
internal interface CrashModule {
    val lastRunCrashVerifier: LastRunCrashVerifier
    val crashService: CrashService
    val automaticVerificationExceptionHandler: AutomaticVerificationExceptionHandler
}

internal class CrashModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    nativeModule: NativeModule,
    sessionModule: SessionModule,
    anrModule: AnrModule,
    dataContainerModule: DataContainerModule,
    androidServicesModule: AndroidServicesModule
) : CrashModule {

    private val crashMarker: CrashFileMarker by singleton {
        val markerFile = lazy {
            storageModule.storageService.getFileForWrite(CrashFileMarker.CRASH_MARKER_FILE_NAME)
        }
        CrashFileMarker(markerFile)
    }

    override val lastRunCrashVerifier: LastRunCrashVerifier by singleton {
        LastRunCrashVerifier(crashMarker)
    }

    override val crashService: CrashService by singleton {
        EmbraceCrashService(
            essentialServiceModule.configService,
            sessionModule.sessionService,
            sessionModule.sessionPropertiesService,
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            deliveryModule.deliveryService,
            essentialServiceModule.userService,
            dataContainerModule.eventService,
            anrModule.anrService,
            nativeModule.ndkService,
            essentialServiceModule.gatingService,
            sessionModule.backgroundActivityService,
            androidServicesModule.preferencesService,
            crashMarker,
            initModule.clock
        )
    }

    override val automaticVerificationExceptionHandler: AutomaticVerificationExceptionHandler by singleton {
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        AutomaticVerificationExceptionHandler(prevHandler)
    }
}
