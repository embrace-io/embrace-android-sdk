package io.embrace.android.embracesdk.internal.instrumentation.crash

import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.ConfigModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.StorageModule
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JsCrashService
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JsCrashServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSourceImpl
import io.embrace.android.embracesdk.internal.payload.AppFramework

internal class CrashModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    instrumentationModule: InstrumentationModule,
) : CrashModule {

    private val crashMarker: CrashFileMarker by singleton {
        val markerFile = lazy {
            storageModule.storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME)
        }
        CrashFileMarkerImpl(markerFile)
    }

    override val jvmCrashDataSource: JvmCrashDataSource by singleton {
        JvmCrashDataSourceImpl(
            essentialServiceModule.sessionPropertiesService,
            androidServicesModule.store,
            instrumentationModule.instrumentationArgs,
            initModule.jsonSerializer,
            jsCrashService?.let { it::appendCrashTelemetryAttributes }
        ).apply {
            addCrashTeardownHandler(crashMarker)
        }
    }

    override val jsCrashService: JsCrashService? by singleton {
        if (configModule.configService.appFramework == AppFramework.REACT_NATIVE) {
            JsCrashServiceImpl(initModule.jsonSerializer)
        } else {
            null
        }
    }

    override val lastRunCrashVerifier: LastRunCrashVerifier by singleton {
        LastRunCrashVerifier(crashMarker)
    }
}
