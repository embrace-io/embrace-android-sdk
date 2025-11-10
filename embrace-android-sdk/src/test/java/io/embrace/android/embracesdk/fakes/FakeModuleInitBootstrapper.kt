package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.fakes.injection.FakeAndroidServicesModule
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeDataCaptureServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeDeliveryModule
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeStorageModule
import io.embrace.android.embracesdk.fakes.injection.FakeSystemServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeWorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModuleSupplier
import io.embrace.android.embracesdk.internal.injection.ConfigModuleSupplier
import io.embrace.android.embracesdk.internal.injection.CoreModuleSupplier
import io.embrace.android.embracesdk.internal.injection.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.DeliveryModuleSupplier
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.InstrumentationModuleSupplier
import io.embrace.android.embracesdk.internal.injection.LogModuleSupplier
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.SessionOrchestrationModuleSupplier
import io.embrace.android.embracesdk.internal.injection.StorageModuleSupplier
import io.embrace.android.embracesdk.internal.injection.SystemServiceModuleSupplier
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCoreModuleSupplier

@Suppress("LongParameterList")
internal fun fakeModuleInitBootstrapper(
    fakeEmbLogger: FakeEmbLogger = FakeEmbLogger(),
    fakeClock: FakeClock = FakeClock(),
    fakeCoreModule: FakeCoreModule = FakeCoreModule(),
    fakeInitModule: FakeInitModule = FakeInitModule(clock = fakeClock, logger = fakeEmbLogger),
    fakeOpenTelemetryModule: FakeOpenTelemetryModule = FakeOpenTelemetryModule(),
    coreModuleSupplier: CoreModuleSupplier = { _, _ -> fakeCoreModule },
    systemServiceModuleSupplier: SystemServiceModuleSupplier = { _, _ -> FakeSystemServiceModule() },
    androidServicesModuleSupplier: AndroidServicesModuleSupplier = { _, _ -> FakeAndroidServicesModule() },
    workerThreadModuleSupplier: WorkerThreadModuleSupplier = { FakeWorkerThreadModule() },
    storageModuleSupplier: StorageModuleSupplier = { _, _, _ -> FakeStorageModule() },
    essentialServiceModuleSupplier: EssentialServiceModuleSupplier = { _, _, _, _, _, _, _, _, _ -> FakeEssentialServiceModule() },
    configModuleSupplier: ConfigModuleSupplier = { _, _, _, _, _ -> FakeConfigModule() },
    instrumentationModuleSupplier: InstrumentationModuleSupplier = { _, _, _, _, _, _ ->
        FakeInstrumentationModule(
            fakeCoreModule.application
        )
    },
    dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = { _, _, _, _ -> FakeDataCaptureServiceModule() },
    deliveryModuleSupplier: DeliveryModuleSupplier = { _, _, _, _, _, _, _, _, _, _, _ -> FakeDeliveryModule() },
    anrModuleSupplier: AnrModuleSupplier = { _, _, _, _, _ -> FakeAnrModule() },
    logModuleSupplier: LogModuleSupplier = { _, _, _, _, _, _, _ -> FakeLogModule() },
    nativeCoreModuleSupplier: NativeCoreModuleSupplier = { _, _, _, _, _, _, _, _, _, _, _ -> FakeNativeCoreModule() },
    sessionOrchestrationModuleSupplier: SessionOrchestrationModuleSupplier =
        { _, _, _, _, _, _, _, _, _, _ -> FakeSessionOrchestrationModule() },
    payloadSourceModuleSupplier: PayloadSourceModuleSupplier =
        { _, _, _, _, _, _, _, _, _, _, _ -> FakePayloadSourceModule() },
) = ModuleInitBootstrapper(
    logger = fakeEmbLogger,
    clock = fakeClock,
    initModule = fakeInitModule,
    openTelemetryModule = fakeOpenTelemetryModule,
    configModuleSupplier = configModuleSupplier,
    coreModuleSupplier = coreModuleSupplier,
    systemServiceModuleSupplier = systemServiceModuleSupplier,
    androidServicesModuleSupplier = androidServicesModuleSupplier,
    workerThreadModuleSupplier = workerThreadModuleSupplier,
    storageModuleSupplier = storageModuleSupplier,
    essentialServiceModuleSupplier = essentialServiceModuleSupplier,
    instrumentationModuleSupplier = instrumentationModuleSupplier,
    dataCaptureServiceModuleSupplier = dataCaptureServiceModuleSupplier,
    deliveryModuleSupplier = deliveryModuleSupplier,
    anrModuleSupplier = anrModuleSupplier,
    logModuleSupplier = logModuleSupplier,
    nativeCoreModuleSupplier = nativeCoreModuleSupplier,
    sessionOrchestrationModuleSupplier = sessionOrchestrationModuleSupplier,
    payloadSourceModuleSupplier = payloadSourceModuleSupplier,
)
