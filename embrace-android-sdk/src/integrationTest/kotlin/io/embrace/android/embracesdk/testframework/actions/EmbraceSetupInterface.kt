package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createAndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.createDeliveryModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.testframework.IntegrationTestRule

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 */
internal class EmbraceSetupInterface @JvmOverloads constructor(
    currentTimeMs: Long = IntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
    var useMockWebServer: Boolean = true,
    @Suppress("DEPRECATION") val appFramework: Embrace.AppFramework = Embrace.AppFramework.NATIVE,
    val overriddenClock: FakeClock = FakeClock(currentTime = currentTimeMs),
    val overriddenInitModule: FakeInitModule = FakeInitModule(clock = overriddenClock, logger = FakeEmbLogger()),
    val overriddenOpenTelemetryModule: OpenTelemetryModule = overriddenInitModule.openTelemetryModule,
    val overriddenCoreModule: FakeCoreModule = FakeCoreModule(
        logger = overriddenInitModule.logger
    ),
    val overriddenConfigService: FakeConfigService = FakeConfigService(
        backgroundActivityCaptureEnabled = true,
        networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true),
        autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(thermalStatusCaptureEnabled = false)
    ),
    val overriddenWorkerThreadModule: WorkerThreadModule = createWorkerThreadModule(
        overriddenInitModule
    ),
    val overriddenAndroidServicesModule: AndroidServicesModule = createAndroidServicesModule(
        initModule = overriddenInitModule,
        coreModule = overriddenCoreModule,
        workerThreadModule = overriddenWorkerThreadModule
    ),
    val fakeAnrModule: AnrModule = FakeAnrModule(),
    val fakeNativeFeatureModule: FakeNativeFeatureModule = FakeNativeFeatureModule(),
    var cacheStorageServiceProvider: Provider<PayloadStorageService?> = { null },
) {
    fun createBootstrapper(): ModuleInitBootstrapper = ModuleInitBootstrapper(
        initModule = overriddenInitModule,
        openTelemetryModule = overriddenInitModule.openTelemetryModule,
        coreModuleSupplier = { _, _ -> overriddenCoreModule },
        workerThreadModuleSupplier = { _ -> overriddenWorkerThreadModule },
        androidServicesModuleSupplier = { _, _, _ -> overriddenAndroidServicesModule },
        deliveryModuleSupplier = { configModule, otelModule, initModule, workerThreadModule, coreModule, storageModule, essentialServiceModule, _, requestExecutionServiceProvider, deliveryServiceProvider ->
            createDeliveryModule(
                configModule,
                otelModule,
                initModule,
                workerThreadModule,
                coreModule,
                storageModule,
                essentialServiceModule,
                cacheStorageServiceProvider = cacheStorageServiceProvider,
                requestExecutionServiceProvider = {
                    when {
                        useMockWebServer -> requestExecutionServiceProvider()
                        else -> FakeRequestExecutionService()
                    }
                },
                deliveryServiceProvider = {
                    when {
                        useMockWebServer -> deliveryServiceProvider()
                        else -> FakeDeliveryService()
                    }
                })
        },
        anrModuleSupplier = { _, _, _, _ -> fakeAnrModule },
        nativeFeatureModuleSupplier = { _, _, _, _, _, _, _, _, _ -> fakeNativeFeatureModule }
    )
}
