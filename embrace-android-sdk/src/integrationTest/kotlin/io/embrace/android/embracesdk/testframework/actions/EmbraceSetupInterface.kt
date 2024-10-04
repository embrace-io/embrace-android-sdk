package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeConfigService
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeNativeFeatureModule
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.behavior.FakeAutoDataCaptureBehavior
import io.embrace.android.embracesdk.fakes.behavior.FakeNetworkSpanForwardingBehavior
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createAndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.createDeliveryModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.testframework.IntegrationTestRule

/**
 * Test harness for which an instance is generated each test run and provided to the test by the Rule
 */
internal class EmbraceSetupInterface @JvmOverloads constructor(
    currentTimeMs: Long = IntegrationTestRule.DEFAULT_SDK_START_TIME_MS,
    @Suppress("DEPRECATION") val appFramework: Embrace.AppFramework = Embrace.AppFramework.NATIVE,
    val overriddenClock: FakeClock = FakeClock(currentTime = currentTimeMs),
    val overriddenInitModule: FakeInitModule = FakeInitModule(clock = overriddenClock),
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
    ) { overriddenConfigService },
    val overriddenAndroidServicesModule: AndroidServicesModule = createAndroidServicesModule(
        initModule = overriddenInitModule,
        coreModule = overriddenCoreModule,
        workerThreadModule = overriddenWorkerThreadModule
    ),
    val fakeAnrModule: AnrModule = FakeAnrModule()
) {
    fun createBootstrapper(): ModuleInitBootstrapper = ModuleInitBootstrapper(
        initModule = overriddenInitModule,
        openTelemetryModule = overriddenInitModule.openTelemetryModule,
        coreModuleSupplier = { _, _ -> overriddenCoreModule },
        workerThreadModuleSupplier = { _, _ -> overriddenWorkerThreadModule },
        androidServicesModuleSupplier = { _, _, _ -> overriddenAndroidServicesModule },
        deliveryModuleSupplier = { configModule, initModule, workerThreadModule, coreModule, storageModule, essentialServiceModule, _, _ ->
            createDeliveryModule(
                configModule,
                initModule,
                workerThreadModule,
                coreModule,
                storageModule,
                essentialServiceModule,
                requestExecutionServiceProvider = { FakeRequestExecutionService() },
                deliveryServiceProvider = { FakeDeliveryService() }
            )
        },
        anrModuleSupplier = { _, _, _, _ -> fakeAnrModule },
        nativeFeatureModuleSupplier = { _, _, _, _, _, _, _, _, _ -> FakeNativeFeatureModule() }
    )
}
