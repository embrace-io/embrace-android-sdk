package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeOpenTelemetryModule
import io.embrace.android.embracesdk.injection.DataSourceModule
import io.embrace.android.embracesdk.injection.DataSourceModuleImpl

internal fun fakeDataSourceModule(): DataSourceModule {
    return DataSourceModuleImpl(
        initModule = FakeInitModule(),
        coreModule = FakeCoreModule(),
        otelModule = FakeOpenTelemetryModule(),
        essentialServiceModule = FakeEssentialServiceModule(),
        systemServiceModule = FakeSystemServiceModule(),
        androidServicesModule = FakeAndroidServicesModule(),
        workerThreadModule = FakeWorkerThreadModule(),
    )
}
