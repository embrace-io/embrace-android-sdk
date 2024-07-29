package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.FeatureModule
import io.embrace.android.embracesdk.internal.capture.FeatureModuleImpl
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSource
import io.embrace.android.embracesdk.internal.capture.aei.AeiDataSourceImpl
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.capture.powersave.LowPowerDataSource
import io.embrace.android.embracesdk.internal.capture.thermalstate.ThermalStateDataSource
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorDataSource
import io.embrace.android.embracesdk.internal.telemetry.errors.InternalErrorDataSourceImpl
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class DataSourceModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    otelModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    anrModule: AnrModule
) : DataSourceModule {

    private val configService = essentialServiceModule.configService

    private val featureModule: FeatureModule by singleton {
        FeatureModuleImpl(
            coreModule,
            initModule,
            otelModule,
            configService
        )
    }

    override val dataCaptureOrchestrator: DataCaptureOrchestrator by singleton {
        DataCaptureOrchestrator(
            configService,
            workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
            initModule.logger
        )
    }

    override val embraceFeatureRegistry: EmbraceFeatureRegistry = dataCaptureOrchestrator

    override val breadcrumbDataSource by dataSourceState(featureModule::breadcrumbDataSource)
    override val tapDataSource by dataSourceState(featureModule::tapDataSource)
    override val pushNotificationDataSource by dataSourceState(featureModule::pushNotificationDataSource)
    override val viewDataSource by dataSourceState(featureModule::viewDataSource)
    override val webViewUrlDataSource by dataSourceState(featureModule::webViewUrlDataSource)
    override val sessionPropertiesDataSource by dataSourceState(featureModule::sessionPropertiesDataSource)
    override val memoryWarningDataSource by dataSourceState(featureModule::memoryWarningDataSource)

    private val aeiService: AeiDataSourceImpl? by singleton {
        if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.R)) {
            AeiDataSourceImpl(
                workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                configService.appExitInfoBehavior,
                systemServiceModule.activityManager,
                androidServicesModule.preferencesService,
                essentialServiceModule.logWriter,
                initModule.logger
            )
        } else {
            null
        }
    }

    override val applicationExitInfoDataSource: DataSourceState<AeiDataSource>? by dataSourceState {
        DataSourceState(
            factory = { aeiService },
            configGate = { configService.isAppExitInfoCaptureEnabled() }
        )
    }

    override val lowPowerDataSource: DataSourceState<LowPowerDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                LowPowerDataSource(
                    context = coreModule.context,
                    backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                    clock = initModule.clock,
                    provider = { systemServiceModule.powerManager },
                    spanService = otelModule.spanService,
                    logger = initModule.logger
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isPowerSaveModeServiceEnabled() }
        )
    }

    override val networkStatusDataSource: DataSourceState<NetworkStatusDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                NetworkStatusDataSource(
                    spanService = otelModule.spanService,
                    logger = initModule.logger
                )
            },
            configGate = { configService.autoDataCaptureBehavior.isNetworkConnectivityServiceEnabled() }
        )
    }

    override val sigquitDataSource: DataSourceState<SigquitDataSource> by dataSourceState {
        DataSourceState(
            factory = anrModule::sigquitDataSource,
            configGate = { configService.anrBehavior.isGoogleAnrCaptureEnabled() }
        )
    }

    override val rnActionDataSource by dataSourceState(featureModule::rnActionDataSource)

    private val thermalService: ThermalStateDataSource? by singleton {
        if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
            ThermalStateDataSource(
                spanService = otelModule.spanService,
                logger = initModule.logger,
                backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                clock = initModule.clock,
                powerManagerProvider = { systemServiceModule.powerManager }
            )
        } else {
            null
        }
    }

    override val thermalStateDataSource: DataSourceState<ThermalStateDataSource>? by dataSourceState {
        DataSourceState(
            factory = { thermalService },
            configGate = {
                configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled() &&
                    configService.sdkModeBehavior.isBetaFeaturesEnabled()
            }
        )
    }

    override val webViewDataSource by dataSourceState(featureModule::webViewDataSource)

    override val internalErrorDataSource: DataSourceState<InternalErrorDataSource> by dataSourceState {
        DataSourceState(
            factory = {
                InternalErrorDataSourceImpl(
                    logWriter = essentialServiceModule.logWriter,
                    logger = initModule.logger,
                )
            },
            configGate = { configService.dataCaptureEventBehavior.isInternalExceptionCaptureEnabled() }
        )
    }

    /**
     * Property delegate that adds the value to a
     * list on its creation. That list is then used by the [DataCaptureOrchestrator] to control
     * the data sources.
     */
    @Suppress("unused")
    private fun <T : DataSource<*>> dataSourceState(provider: Provider<DataSourceState<T>>) =
        DataSourceDelegate(provider, dataCaptureOrchestrator)
}
