package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.DataSourceModule
import io.embrace.android.embracesdk.internal.injection.DataSourceModuleImpl
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.SystemServiceModule
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModule

internal fun fakeDataSourceModule(
    initModule: InitModule = FakeInitModule(),
    coreModule: CoreModule = FakeCoreModule(),
    oTelModule: OpenTelemetryModule = FakeOpenTelemetryModule(),
    essentialServiceModule: EssentialServiceModule = FakeEssentialServiceModule(),
    systemServiceModule: SystemServiceModule = FakeSystemServiceModule(),
    androidServicesModule: io.embrace.android.embracesdk.internal.injection.AndroidServicesModule =
        FakeAndroidServicesModule(),
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
