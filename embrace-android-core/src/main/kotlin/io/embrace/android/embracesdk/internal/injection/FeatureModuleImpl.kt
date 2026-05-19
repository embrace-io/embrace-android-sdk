package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
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

    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> by singleton {
        DataSourceState(
            factory = {
                BreadcrumbDataSource(instrumentationModule.instrumentationArgs)
            }
        ).apply {
            instrumentationModule.instrumentationRegistry.add(this)
        }
    }

    override val internalErrorDataSource: DataSourceState<InternalErrorDataSource> by singleton {
        DataSourceState<InternalErrorDataSource>(
            factory = {
                InternalErrorDataSourceImpl(instrumentationModule.instrumentationArgs)
            },
            configGate = { configService.dataCaptureEventBehavior.isInternalExceptionCaptureEnabled() },
        ).apply {
            instrumentationModule.instrumentationRegistry.add(this)
        }
    }

    override val lastRunCrashVerifier: LastRunCrashVerifier by singleton {
        LastRunCrashVerifier(crashMarker)
    }

    override val crashMarker: CrashFileMarker by singleton {
        val markerFile = lazy {
            storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME)
        }
        CrashFileMarkerImpl(markerFile)
    }
}
