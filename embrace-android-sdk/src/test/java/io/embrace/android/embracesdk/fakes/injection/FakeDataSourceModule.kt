package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.injection.AnrModule
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.injection.DataSourceModuleImpl
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.injection.SystemServiceModule
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal fun fakeDataSourceModule(
    initModule: InitModule = FakeInitModule(),
    coreModule: CoreModule = FakeCoreModule(),
    oTelModule: OpenTelemetryModule = FakeOpenTelemetryModule(),
    essentialServiceModule: EssentialServiceModule = FakeEssentialServiceModule(),
    systemServiceModule: SystemServiceModule = FakeSystemServiceModule(),
    androidServicesModule: AndroidServicesModule = FakeAndroidServicesModule(),
    workerThreadModule: WorkerThreadModule = FakeWorkerThreadModule(),
    anrModule: AnrModule = FakeAnrModule()
): DataSourceModule {
    return DataSourceModuleImpl(
        initModule = initModule,
        coreModule = coreModule,
        otelModule = oTelModule,
        essentialServiceModule = essentialServiceModule,
        systemServiceModule = systemServiceModule,
        androidServicesModule = androidServicesModule,
        workerThreadModule = workerThreadModule,
        anrModule = anrModule
    )
}
