package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.internal.config.ConfigService

internal class FeatureModuleImpl(
    private val featureRegistry: EmbraceFeatureRegistry,
    initModule: InitModule,
    destination: TelemetryDestination,
    configService: ConfigService,
) : FeatureModule {

    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> by singleton {
        DataSourceState(
            factory = {
                BreadcrumbDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    destination = destination,
                    logger = initModule.logger
                )
            }
        ).apply {
            featureRegistry.add(this)
        }
    }

    override val rnActionDataSource: DataSourceState<RnActionDataSource> by singleton {
        DataSourceState(
            factory = {
                RnActionDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    destination,
                    initModule.logger
                )
            }
        ).apply {
            featureRegistry.add(this)
        }
    }

    override val internalErrorDataSource: DataSourceState<InternalErrorDataSource> by singleton {
        DataSourceState<InternalErrorDataSource>(
            factory = {
                InternalErrorDataSourceImpl(
                    destination = destination,
                    logger = initModule.logger,
                )
            },
            configGate = { configService.dataCaptureEventBehavior.isInternalExceptionCaptureEnabled() }
        ).apply {
            featureRegistry.add(this)
        }
    }

    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> by singleton {
        DataSourceState(
            factory = {
                NetworkStatusDataSource(
                    clock = initModule.clock,
                    destination = destination,
                    logger = initModule.logger
                )
            },
            configGate = {
                configService.autoDataCaptureBehavior.isNetworkConnectivityCaptureEnabled()
            }
        ).apply {
            featureRegistry.add(this)
        }
    }
}
