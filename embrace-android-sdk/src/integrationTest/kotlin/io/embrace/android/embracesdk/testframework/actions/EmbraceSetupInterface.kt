@file:Suppress("DEPRECATION")

package io.embrace.android.embracesdk.testframework.actions

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.embrace.android.embracesdk.AppFramework
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeCoreModule
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.NativeCoreModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createAndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.createDeliveryModule
import io.embrace.android.embracesdk.internal.injection.createEssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.testframework.IntegrationTestRule

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 */
internal class EmbraceSetupInterface @JvmOverloads constructor(
    currentTimeMs: Long = IntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
    var useMockWebServer: Boolean = true,
    val appFramework: AppFramework = AppFramework.NATIVE,
    val overriddenClock: FakeClock = FakeClock(currentTime = currentTimeMs),
    val overriddenInitModule: FakeInitModule = FakeInitModule(clock = overriddenClock, logger = FakeEmbLogger()),
    val overriddenOpenTelemetryModule: OpenTelemetryModule = overriddenInitModule.openTelemetryModule,
    val overriddenCoreModule: FakeCoreModule = FakeCoreModule(),
    val overriddenConfigService: FakeConfigService = FakeConfigService(
        backgroundActivityCaptureEnabled = true,
        networkSpanForwardingBehavior = FakeNetworkSpanForwardingBehavior(true),
        autoDataCaptureBehavior = FakeAutoDataCaptureBehavior(thermalStatusCaptureEnabled = false)
    ),
    val overriddenWorkerThreadModule: WorkerThreadModule = createWorkerThreadModule(),
    val overriddenAndroidServicesModule: AndroidServicesModule = createAndroidServicesModule(
        initModule = overriddenInitModule,
        coreModule = overriddenCoreModule,
        workerThreadModule = overriddenWorkerThreadModule
    ),
    val fakeAnrModule: AnrModule = FakeAnrModule(),
    val fakeNativeCoreModule: NativeCoreModule = FakeNativeCoreModule(),
    val fakeNativeFeatureModule: FakeNativeFeatureModule = FakeNativeFeatureModule(),
    var cacheStorageServiceProvider: Provider<PayloadStorageService?> = { null },
    var payloadStorageServiceProvider: Provider<PayloadStorageService?> = { null },
    val networkConnectivityService: FakeNetworkConnectivityService = FakeNetworkConnectivityService(),
    val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner(initialState = Lifecycle.State.INITIALIZED),
) {
    fun createBootstrapper(): ModuleInitBootstrapper = ModuleInitBootstrapper(
        initModule = overriddenInitModule,
        openTelemetryModule = overriddenInitModule.openTelemetryModule,
        coreModuleSupplier = { _ -> overriddenCoreModule },
        workerThreadModuleSupplier = { overriddenWorkerThreadModule },
        androidServicesModuleSupplier = { _, _, _ -> overriddenAndroidServicesModule },
        essentialServiceModuleSupplier = { initModule, configModule, openTelemetryModule, coreModule, workerThreadModule, systemServiceModule, androidServicesModule, storageModule, _, _ ->
            createEssentialServiceModule(
                initModule,
                configModule,
                openTelemetryModule,
                coreModule,
                workerThreadModule,
                systemServiceModule,
                androidServicesModule,
                storageModule,
                { lifecycleOwner }
            ) { networkConnectivityService }
        },
        deliveryModuleSupplier = { configModule, otelModule, initModule, workerThreadModule, coreModule, storageModule, essentialServiceModule, _, _, requestExecutionServiceProvider, deliveryServiceProvider ->
            createDeliveryModule(
                configModule,
                otelModule,
                initModule,
                workerThreadModule,
                coreModule,
                storageModule,
                essentialServiceModule,
                payloadStorageServiceProvider = payloadStorageServiceProvider,
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
        anrModuleSupplier = { _, _, _ -> fakeAnrModule },
        nativeCoreModuleSupplier = { fakeNativeCoreModule },
        nativeFeatureModuleSupplier = { _, _, _, _, _, _, _, _, _ -> fakeNativeFeatureModule }
    )
}
