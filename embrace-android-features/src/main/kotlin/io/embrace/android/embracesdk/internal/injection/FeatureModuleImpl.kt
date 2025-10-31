package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.RnActionDataSource
import io.embrace.android.embracesdk.internal.capture.crumbs.ViewDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.capture.telemetry.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.utils.Provider

internal class FeatureModuleImpl(
    private val featureRegistry: EmbraceFeatureRegistry,
    initModule: InitModule,
    destination: TelemetryDestination,
    configService: ConfigService,
) : FeatureModule {

    override val breadcrumbDataSource: DataSourceState<BreadcrumbDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                BreadcrumbDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    destination = destination,
                    logger = initModule.logger
                )
            }
        )
    }

    override val viewDataSource: DataSourceState<ViewDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                ViewDataSource(
                    configService.breadcrumbBehavior,
                    initModule.clock,
                    destination,
                    initModule.logger
                )
            }
        )
    }

    override val rnActionDataSource: DataSourceState<RnActionDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                RnActionDataSource(
                    breadcrumbBehavior = configService.breadcrumbBehavior,
                    destination,
                    initModule.logger
                )
            }
        )
    }

    override val internalErrorDataSource: DataSourceState<InternalErrorDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                InternalErrorDataSourceImpl(
                    destination = destination,
                    logger = initModule.logger,
                )
            },
            configGate = { configService.dataCaptureEventBehavior.isInternalExceptionCaptureEnabled() }
        )
    }

    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> by dataSourceState {
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
        )
    }

    /**
     * Property delegate that adds the value to a
     * list on its creation. That list is then used by the [DataCaptureOrchestrator] to control
     * the data sources.
     */
    @Suppress("unused")
    private fun <T : DataSource> dataSourceState(provider: Provider<DataSourceState<T>>) =
        DataSourceDelegate(provider, featureRegistry)
}
