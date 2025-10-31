package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crash.CrashDataSource
import io.embrace.android.embracesdk.internal.capture.crash.CrashDataSourceImpl
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier

internal class CrashModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    dataSourceModule: DataSourceModule,
) : CrashModule {

    private val crashMarker: CrashFileMarker by singleton {
        val markerFile = lazy {
            storageModule.storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME)
        }
        CrashFileMarkerImpl(markerFile)
    }

    override val crashDataSource: CrashDataSource by singleton {
        CrashDataSourceImpl(
            essentialServiceModule.sessionPropertiesService,
            androidServicesModule.preferencesService,
            dataSourceModule.instrumentationContext,
            initModule.jsonSerializer,
        ).apply {
            addCrashTeardownHandler(lazy { crashMarker })
        }
    }

    override val lastRunCrashVerifier: LastRunCrashVerifier by singleton {
        LastRunCrashVerifier(crashMarker)
    }
}
