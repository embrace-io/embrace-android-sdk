package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.instrumentation.anr.AnrModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule

/**
 * Contains all the dependency modules that are required by the initialized SDK.
 */
internal interface ModuleGraph {
    val initModule: InitModule
    val openTelemetryModule: OpenTelemetryModule
    val coreModule: CoreModule
    val configModule: ConfigModule
    val workerThreadModule: WorkerThreadModule
    val storageModule: StorageModule
    val essentialServiceModule: EssentialServiceModule
    val dataCaptureServiceModule: DataCaptureServiceModule
    val deliveryModule: DeliveryModule
    val anrModule: AnrModule
    val logModule: LogModule
    val instrumentationModule: InstrumentationModule
    val featureModule: FeatureModule
    val sessionOrchestrationModule: SessionOrchestrationModule
    val payloadSourceModule: PayloadSourceModule
}
