package io.embrace.android.embracesdk.testframework.actions

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.fakes.FakeEmbLogger
import io.embrace.android.embracesdk.fakes.FakeJniDelegate
import io.embrace.android.embracesdk.fakes.FakeNetworkConnectivityService
import io.embrace.android.embracesdk.fakes.FakePayloadStorageService
import io.embrace.android.embracesdk.fakes.FakeRequestExecutionService
import io.embrace.android.embracesdk.fakes.FakeSharedObjectLoader
import io.embrace.android.embracesdk.fakes.FakeSymbolService
import io.embrace.android.embracesdk.fakes.config.FakeInstrumentedConfig
import io.embrace.android.embracesdk.fakes.injection.FakeAnrModule
import io.embrace.android.embracesdk.fakes.injection.FakeCoreModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.delivery.PayloadType
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.SupportedEnvelopeType
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.AnrModule
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.createAndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.createDeliveryModule
import io.embrace.android.embracesdk.internal.injection.createEssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.createNativeCoreModule
import io.embrace.android.embracesdk.internal.injection.createWorkerThreadModule
import io.embrace.android.embracesdk.internal.payload.Envelope
import io.embrace.android.embracesdk.internal.payload.NativeCrashData
import io.embrace.android.embracesdk.internal.payload.SessionPayload
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.testframework.IntegrationTestRule
import java.io.File

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
    var cacheStorageServiceProvider: Provider<PayloadStorageService>? = null,
    var payloadStorageServiceProvider: Provider<PayloadStorageService>? = null,
    val networkConnectivityService: FakeNetworkConnectivityService = FakeNetworkConnectivityService(),
    var jniDelegate: FakeJniDelegate = FakeJniDelegate(),
    var symbols: Map<String, String> = mapOf("libfoo.so" to "symbol_content"),
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
        nativeCoreModuleSupplier = { initModule, coreModule, payloadSourceModule, workerThreadModule, configModule, storageModule, essentialServiceModule, openTelemetryModule, _, _, _ ->
            createNativeCoreModule(
                initModule,
                coreModule,
                payloadSourceModule,
                workerThreadModule,
                configModule,
                storageModule,
                essentialServiceModule,
                openTelemetryModule,
                { jniDelegate },
                ::FakeSharedObjectLoader,
                { FakeSymbolService(symbols) }
            )
        },
    )


    /**
     * Setup a fake dead session on disk
     */
    fun EmbraceSetupInterface.setupFakeDeadSession(
        storageService: FakePayloadStorageService,
        sessionMetadata: StoredTelemetryMetadata,
        deadSessionEnvelope: Envelope<SessionPayload>,
    ) {
        storageService.addPayload(sessionMetadata, deadSessionEnvelope)
        cacheStorageServiceProvider = { storageService }
    }

    /**
     * Setup a fake native crash on disk
     */
    fun EmbraceSetupInterface.setupFakeNativeCrash(
        serializer: PlatformSerializer,
        crashData: NativeCrashData,
        name: String = "uuid",
    ) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val outputDir = StorageLocation.NATIVE.asFile(ctx, FakeEmbLogger()).value.apply {
            mkdirs()
        }
        val metadata = StoredTelemetryMetadata(
            timestamp = 1000000,
            uuid = name,
            processId = "pid",
            envelopeType = SupportedEnvelopeType.CRASH,
            payloadType = PayloadType.NATIVE_CRASH,
        )
        File(outputDir, metadata.filename).createNewFile()
        jniDelegate.crashRaw = serializer.toJson(crashData, NativeCrashData::class.java)
    }
}
