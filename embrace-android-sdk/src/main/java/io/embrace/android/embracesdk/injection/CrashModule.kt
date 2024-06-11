package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.crash.CrashDataSourceImpl
import io.embrace.android.embracesdk.capture.crash.CrashService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.ndk.NativeCrashDataSourceImpl
import io.embrace.android.embracesdk.ndk.NativeCrashService
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.ndk.NoopNativeCrashService
import io.embrace.android.embracesdk.samples.AutomaticVerificationExceptionHandler

/**
 * Contains dependencies that capture crashes
 */
internal interface CrashModule {
    val lastRunCrashVerifier: LastRunCrashVerifier
    val crashService: CrashService
    val automaticVerificationExceptionHandler: AutomaticVerificationExceptionHandler
    val nativeCrashService: NativeCrashService
}

internal class CrashModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    sessionModule: SessionModule,
    anrModule: AnrModule,
    androidServicesModule: AndroidServicesModule,
    logModule: CustomerLogModule
) : CrashModule {

    private val crashMarker: CrashFileMarker by singleton {
        val markerFile = lazy {
            storageModule.storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME)
        }
        CrashFileMarkerImpl(markerFile, initModule.logger)
    }

    override val crashService: CrashService by singleton {
        CrashDataSourceImpl(
            logModule.logOrchestrator,
            sessionModule.sessionOrchestrator,
            essentialServiceModule.sessionProperties,
            anrModule.anrService,
            nativeModule.ndkService,
            androidServicesModule.preferencesService,
            crashMarker,
            essentialServiceModule.logWriter,
            essentialServiceModule.configService,
            initModule.jsonSerializer,
            initModule.logger,
        )
    }

    override val lastRunCrashVerifier: LastRunCrashVerifier by singleton {
        LastRunCrashVerifier(crashMarker, initModule.logger)
    }

    override val automaticVerificationExceptionHandler: AutomaticVerificationExceptionHandler by singleton {
        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        AutomaticVerificationExceptionHandler(prevHandler, initModule.logger)
    }

    override val nativeCrashService: NativeCrashService by singleton {
        if (!essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled()) {
            NoopNativeCrashService()
        } else if (essentialServiceModule.configService.oTelBehavior.isBetaEnabled()) {
            NativeCrashDataSourceImpl(
                sessionProperties = essentialServiceModule.sessionProperties,
                ndkService = nativeModule.ndkService,
                preferencesService = androidServicesModule.preferencesService,
                logWriter = essentialServiceModule.logWriter,
                configService = essentialServiceModule.configService,
                serializer = initModule.jsonSerializer,
                logger = initModule.logger,
            )
        } else {
            nativeModule.ndkService
        }
    }
}
