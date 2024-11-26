package io.embrace.android.embracesdk.testframework.actions

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeNativeCoreModule
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
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
    val overriddenClock: FakeClock = FakeClock(currentTime = currentTimeMs),
    val overriddenInitModule: FakeInitModule = FakeInitModule(clock = overriddenClock, logger = FakeEmbLogger()),
    val overriddenOpenTelemetryModule: OpenTelemetryModule = overriddenInitModule.openTelemetryModule,
    val overriddenCoreModule: FakeCoreModule = FakeCoreModule(),
    val overriddenWorkerThreadModule: WorkerThreadModule = createWorkerThreadModule(),
    val overriddenAndroidServicesModule: AndroidServicesModule = createAndroidServicesModule(
        initModule = overriddenInitModule,
        coreModule = overriddenCoreModule
    ),
    val fakeAnrModule: AnrModule = FakeAnrModule(),
    val fakeNativeFeatureModule: FakeNativeFeatureModule = FakeNativeFeatureModule(),
    var cacheStorageServiceProvider: Provider<PayloadStorageService>? = null,
    var payloadStorageServiceProvider: Provider<PayloadStorageService>? = null,
    val networkConnectivityService: FakeNetworkConnectivityService = FakeNetworkConnectivityService(),
    val lifecycleOwner: TestLifecycleOwner = TestLifecycleOwner(initialState = Lifecycle.State.INITIALIZED),
) {
    fun createBootstrapper(
        instrumentedConfig: FakeInstrumentedConfig,
    ): ModuleInitBootstrapper = ModuleInitBootstrapper(
        initModule = overriddenInitModule.apply {
            this.instrumentedConfig = instrumentedConfig
        },
        openTelemetryModule = overriddenInitModule.openTelemetryModule,
        coreModuleSupplier = { _, _ -> overriddenCoreModule },
        workerThreadModuleSupplier = { overriddenWorkerThreadModule },
        androidServicesModuleSupplier = { _, _ -> overriddenAndroidServicesModule },
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
        deliveryModuleSupplier = { configModule, initModule, otelModule, workerThreadModule, coreModule, storageModule, essentialServiceModule, androidServicesModule, _, _, _, _, _ ->
            val requestExecutionServiceProvider: Provider<RequestExecutionService>? = when {
                useMockWebServer -> null
                else -> ::FakeRequestExecutionService
            }
            val deliveryServiceProvider: Provider<DeliveryService>? = when {
                useMockWebServer -> null
                else -> ::FakeDeliveryService
            }
            createDeliveryModule(
                configModule,
                initModule,
                otelModule,
                workerThreadModule,
                coreModule,
                storageModule,
                essentialServiceModule,
                androidServicesModule,
                payloadStorageServiceProvider = payloadStorageServiceProvider,
                cacheStorageServiceProvider = cacheStorageServiceProvider,
                requestExecutionServiceProvider = requestExecutionServiceProvider,
                deliveryServiceProvider = deliveryServiceProvider,
                deliveryTracer = DeliveryTracer()
            )
        },
        anrModuleSupplier = { _, _, _ -> fakeAnrModule },
        nativeCoreModuleSupplier = { _, _, _, _, _, _, _, _ -> FakeNativeCoreModule() },
        nativeFeatureModuleSupplier = { _, _, _, _, _, _, _ -> fakeNativeFeatureModule }
    )
}
