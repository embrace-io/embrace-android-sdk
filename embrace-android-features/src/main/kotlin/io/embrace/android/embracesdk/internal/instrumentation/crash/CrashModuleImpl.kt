package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.StorageModule
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.CrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.CrashDataSourceImpl

internal class CrashModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    instrumentationModule: InstrumentationModule,
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
            instrumentationModule.instrumentationArgs,
            initModule.jsonSerializer,
        ).apply {
            addCrashTeardownHandler(lazy { crashMarker })
        }
    }

    override val lastRunCrashVerifier: LastRunCrashVerifier by singleton {
        LastRunCrashVerifier(crashMarker)
    }
}
