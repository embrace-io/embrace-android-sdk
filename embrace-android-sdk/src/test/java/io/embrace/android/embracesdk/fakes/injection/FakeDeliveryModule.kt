package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.FakeWorkerThreadModule
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule
import io.embrace.android.embracesdk.injection.DeliveryModule
import io.embrace.android.embracesdk.injection.DeliveryModuleImpl
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.InitModuleImpl
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal class FakeDeliveryModule(
    initModule: InitModule = InitModuleImpl(),
    coreModule: CoreModule = FakeCoreModule(),
    essentialServiceModule: EssentialServiceModule = FakeEssentialServiceModule(),
    dataCaptureServiceModule: DataCaptureServiceModule = FakeDataCaptureServiceModule(),
    workerThreadModule: WorkerThreadModule = FakeWorkerThreadModule(),
    deliveryServiceImpl: DeliveryModule = DeliveryModuleImpl(
        initModule = initModule,
        coreModule = coreModule,
        essentialServiceModule = essentialServiceModule,
        dataCaptureServiceModule = dataCaptureServiceModule,
        workerThreadModule = workerThreadModule
    )
) : DeliveryModule by deliveryServiceImpl {
    override val deliveryService: FakeDeliveryService = FakeDeliveryService()
}
