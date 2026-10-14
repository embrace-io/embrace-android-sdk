package io.embrace.android.embracesdk.fakes

import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.fakes.injection.FakeEssentialServiceModule
import io.embrace.android.embracesdk.fakes.injection.FakeInitModule
import io.embrace.android.embracesdk.fakes.injection.FakeLogModule
import io.embrace.android.embracesdk.fakes.injection.FakePayloadSourceModule
import io.embrace.android.embracesdk.fakes.injection.FakeUserSessionOrchestrationModule
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.injection.CoreModule
import io.embrace.android.embracesdk.internal.injection.DeliveryModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.FeatureModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.LogModule
import io.embrace.android.embracesdk.internal.injection.ModuleGraph
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.PayloadSourceModule
import io.embrace.android.embracesdk.internal.injection.UserSessionOrchestrationModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.storage.StorageService

/**
 * A [ModuleGraph] assembled from fakes, for testing code that reads modules off the graph without
 * needing to initialize the SDK. Modules that have no fake throw if accessed.
 */
internal class FakeModuleGraph(
    override val initModule: InitModule = FakeInitModule(otelSdkMode = OtelSdkMode.COMPAT),
    override val openTelemetryModule: OpenTelemetryModule = FakeOpenTelemetryModule(otelSdkMode = OtelSdkMode.COMPAT),
    override val configService: ConfigService = FakeConfigService(),
    override val essentialServiceModule: EssentialServiceModule = FakeEssentialServiceModule(),
    override val logModule: LogModule = FakeLogModule(),
    override val instrumentationModule: InstrumentationModule =
        FakeInstrumentationModule(ApplicationProvider.getApplicationContext()),
    override val userSessionOrchestrationModule: UserSessionOrchestrationModule = FakeUserSessionOrchestrationModule(),
    override val payloadSourceModule: PayloadSourceModule = FakePayloadSourceModule(),
    override val deliveryModule: DeliveryModule? = null,
    override val threadBlockageService: ThreadBlockageService? = null,
) : ModuleGraph {
    override val sdkStartTimeMs: Long = 0
    override val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker get() = notFaked()
    override val coreModule: CoreModule get() = notFaked()
    override val workerThreadModule: WorkerThreadModule get() = notFaked()
    override val storageService: StorageService get() = notFaked()
    override val dataCaptureServiceModule: DataCaptureServiceModule get() = notFaked()
    override val featureModule: FeatureModule get() = notFaked()

    private fun notFaked(): Nothing = error("Not faked")
}
