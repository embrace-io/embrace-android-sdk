package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.storage.StorageService

/**
 * Contains all the dependency modules that are required by the initialized SDK.
 */
internal interface ModuleGraph {
    val initModule: InitModule
    val openTelemetryModule: OpenTelemetryModule
    val coreModule: CoreModule
    val configService: ConfigService
    val workerThreadModule: WorkerThreadModule
    val storageService: StorageService
    val essentialServiceModule: EssentialServiceModule
    val dataCaptureServiceModule: DataCaptureServiceModule
    val deliveryModule: DeliveryModule
    val threadBlockageService: ThreadBlockageService?
    val logModule: LogModule
    val instrumentationModule: InstrumentationModule
    val featureModule: FeatureModule
    val sessionOrchestrator: SessionOrchestrator
    val payloadSourceModule: PayloadSourceModule
}
