package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.storage.StorageService

class FeatureModuleImpl(
    instrumentationModule: InstrumentationModule,
    configService: ConfigService,
    storageService: StorageService,
) : FeatureModule {

    override val breadcrumbDataSource: BreadcrumbDataSource? by lazy {
        instrumentationModule.instrumentationRegistry.add {
            BreadcrumbDataSource(instrumentationModule.instrumentationArgs)
        }
    }

    override val internalErrorDataSource: InternalErrorDataSource? by lazy {
        instrumentationModule.instrumentationRegistry.add {
            if (configService.dataCaptureEventBehavior.isInternalExceptionCaptureEnabled()) {
                InternalErrorDataSourceImpl(instrumentationModule.instrumentationArgs)
            } else {
                null
            }
        }
    }

    override val crashMarker: CrashFileMarker = CrashFileMarkerImpl(
        lazy { storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME) },
    )

    override val lastRunCrashVerifier: LastRunCrashVerifier = LastRunCrashVerifier(crashMarker)
}
