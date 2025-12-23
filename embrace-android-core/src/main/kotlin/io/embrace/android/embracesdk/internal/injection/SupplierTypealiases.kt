package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.config.ConfigModule
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.Provider

typealias ConfigModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
) -> ConfigModule

typealias CoreModuleSupplier = (
    context: Context,
    initModule: InitModule,
) -> CoreModule

typealias DeliveryModuleSupplier = (
    configModule: ConfigModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    payloadStorageServiceProvider: Provider<PayloadStorageService>?,
    cacheStorageServiceProvider: Provider<PayloadStorageService>?,
    requestExecutionServiceProvider: Provider<RequestExecutionService>?,
    deliveryTracer: DeliveryTracer?,
) -> DeliveryModule

typealias EssentialServiceModuleSupplier = (
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    lifecycleOwnerProvider: Provider<LifecycleOwner?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
) -> EssentialServiceModule

typealias FeatureModuleSupplier = (
    instrumentationModule: InstrumentationModule,
    configService: ConfigService,
    storageService: StorageService,
) -> FeatureModule

typealias InstrumentationModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    coreModule: CoreModule,
    storageService: StorageService,
) -> InstrumentationModule

typealias LogModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    workerThreadModule: WorkerThreadModule,
    payloadSourceModule: PayloadSourceModule,
) -> LogModule

typealias PayloadSourceModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    otelModule: OpenTelemetryModule,
    otelPayloadMapper: OtelPayloadMapper?,
    deliveryModule: DeliveryModule,
) -> PayloadSourceModule

typealias SessionOrchestratorSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    deliveryModule: DeliveryModule,
    instrumentationModule: InstrumentationModule,
    payloadSourceModule: PayloadSourceModule,
    startupDurationProvider: () -> Long?,
    logModule: LogModule,
) -> SessionOrchestrator

typealias StorageServiceSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) -> StorageService

typealias WorkerThreadModuleSupplier = () -> WorkerThreadModule
