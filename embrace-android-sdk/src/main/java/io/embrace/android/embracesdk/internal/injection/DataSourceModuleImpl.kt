package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.anr.sigquit.SigquitDataSource
import io.embrace.android.embracesdk.internal.arch.DataCaptureOrchestrator
import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.capture.FeatureModule
import io.embrace.android.embracesdk.internal.capture.FeatureModuleImpl
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
            coreModule = coreModule,
            initModule = initModule,
            otelModule = otelModule,
            workerThreadModule = workerThreadModule,
            systemServiceModule = systemServiceModule,
            androidServicesModule = androidServicesModule,
            logWriter = essentialServiceModule.logWriter,
            configService = configService
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
    override val applicationExitInfoDataSource by dataSourceState(featureModule::applicationExitInfoDataSource)
    override val lowPowerDataSource by dataSourceState(featureModule::lowPowerDataSource)
    override val networkStatusDataSource by dataSourceState(featureModule::networkStatusDataSource)

    override val sigquitDataSource: DataSourceState<SigquitDataSource> by dataSourceState {
        DataSourceState(
            factory = anrModule::sigquitDataSource,
            configGate = { configService.anrBehavior.isGoogleAnrCaptureEnabled() }
        )
    }

    override val rnActionDataSource by dataSourceState(featureModule::rnActionDataSource)
    override val thermalStateDataSource by dataSourceState(featureModule::thermalStateDataSource)
    override val webViewDataSource by dataSourceState(featureModule::webViewDataSource)
    override val internalErrorDataSource by dataSourceState(featureModule::internalErrorDataSource)

    /**
     * Property delegate that adds the value to a
     * list on its creation. That list is then used by the [DataCaptureOrchestrator] to control
     * the data sources.
     */
    @Suppress("unused")
    private fun <T : DataSource<*>> dataSourceState(provider: Provider<DataSourceState<T>>) =
        DataSourceDelegate(provider, dataCaptureOrchestrator)
}
