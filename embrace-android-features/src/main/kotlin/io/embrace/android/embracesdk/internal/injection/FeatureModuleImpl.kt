package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.internal.config.ConfigService

internal class FeatureModuleImpl(
    instrumentationModule: InstrumentationModule,
    configService: ConfigService,
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

    override val rnActionDataSource: DataSourceState<RnActionDataSource> by singleton {
        DataSourceState(
            factory = {
                RnActionDataSource(instrumentationModule.instrumentationArgs)
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
            configGate = { configService.dataCaptureEventBehavior.isInternalExceptionCaptureEnabled() }
        ).apply {
            instrumentationModule.instrumentationRegistry.add(this)
        }
    }

    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> by singleton {
        DataSourceState(
            factory = {
                NetworkStatusDataSource(instrumentationModule.instrumentationArgs)
            },
            configGate = {
                configService.autoDataCaptureBehavior.isNetworkConnectivityCaptureEnabled()
            }
        ).apply {
            instrumentationModule.instrumentationRegistry.add(this)
        }
    }
}
