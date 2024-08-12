package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeCrashModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataContainerModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeModule
import io.embrace.android.embracesdk.fakes.injection.FakeSessionModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModuleSupplier
import io.embrace.android.embracesdk.internal.injection.AnrModuleSupplier
import io.embrace.android.embracesdk.internal.injection.CoreModuleSupplier
import io.embrace.android.embracesdk.internal.injection.CrashModuleSupplier
import io.embrace.android.embracesdk.internal.injection.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.DataContainerModuleSupplier
import io.embrace.android.embracesdk.internal.injection.DataSourceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.DeliveryModuleSupplier
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.LogModuleSupplier
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.NativeModuleSupplier
import io.embrace.android.embracesdk.internal.injection.PayloadModuleSupplier
import io.embrace.android.embracesdk.internal.injection.SessionModuleSupplier
import io.embrace.android.embracesdk.internal.injection.StorageModuleSupplier
import io.embrace.android.embracesdk.internal.injection.SystemServiceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModuleSupplier

@Suppress("LongParameterList")
internal fun fakeModuleInitBootstrapper(
    fakeEmbLogger: FakeEmbLogger = FakeEmbLogger(),
    fakeInitModule: FakeInitModule = FakeInitModule(logger = fakeEmbLogger),
    fakeOpenTelemetryModule: FakeOpenTelemetryModule = FakeOpenTelemetryModule(),
    coreModuleSupplier: CoreModuleSupplier = { _, _ -> FakeCoreModule() },
    systemServiceModuleSupplier: SystemServiceModuleSupplier = { _, _ -> FakeSystemServiceModule() },
    androidServicesModuleSupplier: AndroidServicesModuleSupplier = { _, _, _ -> FakeAndroidServicesModule() },
    workerThreadModuleSupplier: WorkerThreadModuleSupplier = { _ -> FakeWorkerThreadModule() },
    storageModuleSupplier: StorageModuleSupplier = { _, _, _ -> FakeStorageModule() },
    essentialServiceModuleSupplier: EssentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _, _, _, _ -> FakeEssentialServiceModule() },
    dataSourceModuleSupplier: DataSourceModuleSupplier = { _, _, _ -> FakeDataSourceModule() },
    dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = { _, _, _, _, _, _ -> FakeDataCaptureServiceModule() },
    deliveryModuleSupplier: DeliveryModuleSupplier = { _, _, _, _ -> FakeDeliveryModule() },
    anrModuleSupplier: AnrModuleSupplier = { _, _, _, _ -> FakeAnrModule() },
    logModuleSupplier: LogModuleSupplier = { _, _, _, _, _, _, _ -> FakeLogModule() },
    nativeModuleSupplier: NativeModuleSupplier = { _, _, _, _, _, _, _ -> FakeNativeModule() },
    dataContainerModuleSupplier: DataContainerModuleSupplier = { _, _, _, _, _ -> FakeDataContainerModule() },
    sessionModuleSupplier: SessionModuleSupplier = { _, _, _, _, _, _, _, _, _, _, _ -> FakeSessionModule() },
    crashModuleSupplier: CrashModuleSupplier = { _, _, _, _, _ -> FakeCrashModule() },
    payloadModuleSupplier: PayloadModuleSupplier = { _, _, _, _, _, _, _, _, _ -> FakePayloadModule() }
) = ModuleInitBootstrapper(
    logger = fakeEmbLogger,
    initModule = fakeInitModule,
    openTelemetryModule = fakeOpenTelemetryModule,
    coreModuleSupplier = coreModuleSupplier,
    systemServiceModuleSupplier = systemServiceModuleSupplier,
    androidServicesModuleSupplier = androidServicesModuleSupplier,
    workerThreadModuleSupplier = workerThreadModuleSupplier,
    storageModuleSupplier = storageModuleSupplier,
    essentialServiceModuleSupplier = essentialServiceModuleSupplier,
    dataSourceModuleSupplier = dataSourceModuleSupplier,
    dataCaptureServiceModuleSupplier = dataCaptureServiceModuleSupplier,
    deliveryModuleSupplier = deliveryModuleSupplier,
    anrModuleSupplier = anrModuleSupplier,
    logModuleSupplier = logModuleSupplier,
    nativeModuleSupplier = nativeModuleSupplier,
    dataContainerModuleSupplier = dataContainerModuleSupplier,
    sessionModuleSupplier = sessionModuleSupplier,
    crashModuleSupplier = crashModuleSupplier,
    payloadModuleSupplier = payloadModuleSupplier,
)
