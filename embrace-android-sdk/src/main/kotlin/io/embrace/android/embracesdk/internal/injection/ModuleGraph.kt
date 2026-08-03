package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.storage.StorageService

/**
 * Contains all the dependency modules that are required by the initialized SDK.
 */
internal interface ModuleGraph {
    val sdkStartTimeMs: Long
    val initModule: InitModule
    val openTelemetryModule: OpenTelemetryModule
    val coreModule: CoreModule
    val configService: ConfigService

    /**
     * The number of times the SDK has begun startup on the current app version, including this
     * startup. Incremented as soon as the module graph is being constructed so that startups
     * that never complete are still counted. -1 if the counter could not be determined.
     */
    val appVersionStartupCounter: Int
    val workerThreadModule: WorkerThreadModule
    val storageService: StorageService
    val essentialServiceModule: EssentialServiceModule
    val dataCaptureServiceModule: DataCaptureServiceModule
    val deliveryModule: DeliveryModule?
    val threadBlockageService: ThreadBlockageService?
    val logModule: LogModule
    val instrumentationModule: InstrumentationModule
    val featureModule: FeatureModule
    val userSessionOrchestrationModule: UserSessionOrchestrationModule
    val payloadSourceModule: PayloadSourceModule
}
