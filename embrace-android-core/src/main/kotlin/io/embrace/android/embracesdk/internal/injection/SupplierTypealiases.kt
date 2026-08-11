package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.PersistedConfig
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.envelope.session.OtelPayloadMapper
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.Provider

typealias ConfigServiceSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    persistedConfig: PersistedConfig,
) -> ConfigService

typealias CoreModuleSupplier = (
    context: Context,
    initModule: InitModule,
    keyValueStore: Lazy<KeyValueStore>,
) -> CoreModule

typealias DeliveryModuleSupplier = (
    configService: ConfigService,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    payloadStorageServiceProvider: Provider<PayloadStorageService>?,
    cacheStorageServiceProvider: Provider<PayloadStorageService>?,
    requestExecutionServiceProvider: Provider<RequestExecutionService>?,
    deliveryTracer: DeliveryTracer?,
) -> DeliveryModule?

typealias EssentialServiceModuleSupplier = (
    initModule: InitModule,
    configService: ConfigService,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    startupContext: Context?,
    lifecycleTrackerProvider: Provider<LifecycleTracker?>,
    networkConnectivityServiceProvider: Provider<NetworkConnectivityService?>,
    sessionOrchestratorProvider: Provider<SessionOrchestrator>,
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
    configService: ConfigService,
    essentialServiceModule: EssentialServiceModule,
    coreModule: CoreModule,
    storageService: StorageService,
    userSessionIdsProvider: () -> String?,
    activeSessionIdsProvider: () -> SessionIdsSnapshot,
) -> InstrumentationModule

typealias LogModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    deliveryModule: DeliveryModule?,
    workerThreadModule: WorkerThreadModule,
    payloadSourceModule: PayloadSourceModule,
) -> LogModule

typealias PayloadSourceModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    otelModule: OpenTelemetryModule,
    otelPayloadMapper: OtelPayloadMapper?,
    deliveryModule: DeliveryModule?,
) -> PayloadSourceModule

typealias UserSessionOrchestrationModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    configService: ConfigService,
    deliveryModule: DeliveryModule?,
    instrumentationModule: InstrumentationModule,
    payloadSourceModule: PayloadSourceModule,
    startupDurationProvider: () -> Long?,
    appVersionStartupCounterProvider: () -> Int?,
    logModule: LogModule,
    workerThreadModule: WorkerThreadModule,
) -> UserSessionOrchestrationModule

typealias StorageServiceSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) -> StorageService

typealias WorkerThreadModuleSupplier = () -> WorkerThreadModule
