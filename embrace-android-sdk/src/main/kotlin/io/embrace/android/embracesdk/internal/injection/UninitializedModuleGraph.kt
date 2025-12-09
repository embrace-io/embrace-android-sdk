package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService

internal class SdkDisabledException : IllegalStateException()

/**
 * Uninitialized modules that will throw an IllegalStateException if they are accessed.
 */
internal object UninitializedModuleGraph : ModuleGraph {
    override val initModule: InitModule get() = throwSdkNotInitialized()
    override val openTelemetryModule: OpenTelemetryModule get() = throwSdkNotInitialized()
    override val coreModule: CoreModule get() = throwSdkNotInitialized()
    override val configModule: ConfigModule get() = throwSdkNotInitialized()
    override val workerThreadModule: WorkerThreadModule get() = throwSdkNotInitialized()
    override val storageModule: StorageModule get() = throwSdkNotInitialized()
    override val essentialServiceModule: EssentialServiceModule get() = throwSdkNotInitialized()
    override val dataCaptureServiceModule: DataCaptureServiceModule get() = throwSdkNotInitialized()
    override val deliveryModule: DeliveryModule get() = throwSdkNotInitialized()
    override val threadBlockageService: ThreadBlockageService get() = throwSdkNotInitialized()
    override val logModule: LogModule get() = throwSdkNotInitialized()
    override val instrumentationModule: InstrumentationModule get() = throwSdkNotInitialized()
    override val featureModule: FeatureModule get() = throwSdkNotInitialized()
    override val sessionOrchestrationModule: SessionOrchestrationModule get() = throwSdkNotInitialized()
    override val payloadSourceModule: PayloadSourceModule get() = throwSdkNotInitialized()

    private fun throwSdkNotInitialized(): Nothing = error("SDK not initialized")
}
