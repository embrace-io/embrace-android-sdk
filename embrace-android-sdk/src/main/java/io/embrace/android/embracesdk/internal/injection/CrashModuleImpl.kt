package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crash.CrashDataSource
import io.embrace.android.embracesdk.internal.capture.crash.CrashDataSourceImpl
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.ndk.NativeCrashDataSourceImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.ndk.NoopNativeCrashService

internal class CrashModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    nativeModule: NativeModule,
    androidServicesModule: AndroidServicesModule
) : CrashModule {

    private val crashMarker: CrashFileMarker by singleton {
        val markerFile = lazy {
            storageModule.storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME)
        }
        CrashFileMarkerImpl(markerFile, initModule.logger)
    }

    override val crashDataSource: CrashDataSource by singleton {
        CrashDataSourceImpl(
            essentialServiceModule.sessionProperties,
            nativeModule.ndkService,
            androidServicesModule.preferencesService,
            essentialServiceModule.logWriter,
            essentialServiceModule.configService,
            initModule.jsonSerializer,
            initModule.logger,
        ).apply {
            addCrashTeardownHandler(crashMarker)
        }
    }

    override val lastRunCrashVerifier: LastRunCrashVerifier by singleton {
        LastRunCrashVerifier(crashMarker, initModule.logger)
    }

    override val nativeCrashService: NativeCrashService by singleton {
        if (!essentialServiceModule.configService.autoDataCaptureBehavior.isNdkEnabled()) {
            NoopNativeCrashService()
        } else {
            NativeCrashDataSourceImpl(
                sessionProperties = essentialServiceModule.sessionProperties,
                ndkService = nativeModule.ndkService,
                preferencesService = androidServicesModule.preferencesService,
                logWriter = essentialServiceModule.logWriter,
                configService = essentialServiceModule.configService,
                serializer = initModule.jsonSerializer,
                logger = initModule.logger,
            )
        }
    }
}
