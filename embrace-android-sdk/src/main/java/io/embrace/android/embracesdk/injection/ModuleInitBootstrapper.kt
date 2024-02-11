package io.embrace.android.embracesdk.injection

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.utils.AndroidServicesModuleSupplier
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.CoreModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DeliveryModuleSupplier
import io.embrace.android.embracesdk.internal.utils.EssentialServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.StorageModuleSupplier
import io.embrace.android.embracesdk.internal.utils.SystemServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.WorkerThreadModuleSupplier
import io.embrace.android.embracesdk.worker.TaskPriority
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A class that wires together and initializes modules in a manner that makes them work as a cohesive whole.
 */
internal class ModuleInitBootstrapper(
    val initModule: InitModule = InitModuleImpl(),
    val openTelemetryModule: OpenTelemetryModule = OpenTelemetryModuleImpl(initModule),
    private val coreModuleSupplier: CoreModuleSupplier = ::CoreModuleImpl,
    private val systemServiceModuleSupplier: SystemServiceModuleSupplier = ::SystemServiceModuleImpl,
    private val androidServicesModuleSupplier: AndroidServicesModuleSupplier = ::AndroidServicesModuleImpl,
    private val workerThreadModuleSupplier: WorkerThreadModuleSupplier = ::WorkerThreadModuleImpl,
    private val storageModuleSupplier: StorageModuleSupplier = ::StorageModuleImpl,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier = ::EssentialServiceModuleImpl,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier = ::DataCaptureServiceModuleImpl,
    private val deliveryModuleSupplier: DeliveryModuleSupplier = ::DeliveryModuleImpl,
) {
    lateinit var coreModule: CoreModule
        private set

    lateinit var workerThreadModule: WorkerThreadModule
        private set

    lateinit var systemServiceModule: SystemServiceModule
        private set

    lateinit var androidServicesModule: AndroidServicesModule
        private set

    lateinit var storageModule: StorageModule
        private set

    lateinit var essentialServiceModule: EssentialServiceModule
        private set

    lateinit var dataCaptureServiceModule: DataCaptureServiceModule
        private set

    lateinit var deliveryModule: DeliveryModule
        private set

    private val asyncInitTask = AtomicReference<Future<*>?>(null)

    /**
     * Returns true when the call has triggered an initialization, false if initialization is already in progress or is complete.
     */
    @JvmOverloads
    fun init(
        context: Context,
        enableIntegrationTesting: Boolean,
        appFramework: AppFramework,
        sdkStartTimeNanos: Long,
        customAppId: String? = null,
        configServiceProvider: Provider<ConfigService?> = { null },
        versionChecker: VersionChecker = BuildVersionChecker,
    ): Boolean {
        try {
            Systrace.startSynchronous("modules-init")
            if (asyncInitTask.get() != null) {
                return false
            }

            synchronized(asyncInitTask) {
                return if (asyncInitTask.get() == null) {
                    coreModule = Systrace.traceSynchronous("core-init") { coreModuleSupplier(context, appFramework) }
                    workerThreadModule = Systrace.traceSynchronous("worker-init") { workerThreadModuleSupplier(initModule) }
                    val initTask = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION).submit(TaskPriority.CRITICAL) {
                        Systrace.trace("spans-service-init") {
                            openTelemetryModule.spansService.initializeService(sdkStartTimeNanos)
                        }
                    }
                    systemServiceModule = Systrace.traceSynchronous("system-service-init") {
                        systemServiceModuleSupplier(coreModule, versionChecker)
                    }
                    androidServicesModule = Systrace.traceSynchronous("android-service-init") {
                        androidServicesModuleSupplier(initModule, coreModule, workerThreadModule)
                    }
                    storageModule = Systrace.traceSynchronous("storage-init") {
                        storageModuleSupplier(initModule, coreModule, workerThreadModule)
                    }
                    essentialServiceModule = Systrace.traceSynchronous("essential-init") {
                        essentialServiceModuleSupplier(
                            initModule,
                            coreModule,
                            workerThreadModule,
                            systemServiceModule,
                            androidServicesModule,
                            storageModule,
                            customAppId,
                            enableIntegrationTesting,
                            configServiceProvider
                        )
                    }
                    dataCaptureServiceModule = Systrace.traceSynchronous("data-capture-init") {
                        dataCaptureServiceModuleSupplier(
                            initModule,
                            openTelemetryModule,
                            coreModule,
                            systemServiceModule,
                            essentialServiceModule,
                            workerThreadModule,
                            versionChecker
                        )
                    }
                    deliveryModule = Systrace.traceSynchronous("delivery-init") {
                        deliveryModuleSupplier(coreModule, workerThreadModule, storageModule, essentialServiceModule)
                    }
                    asyncInitTask.set(initTask)
                    true
                } else {
                    false
                }
            }
        } finally {
            Systrace.endSynchronous()
        }
    }

    /**
     * A blocking get that returns when the async portion of initialization is complete. An exception will be thrown by the underlying
     * [Future] if there is a timeout or if this failed for other reasons.
     */
    @JvmOverloads
    fun waitForAsyncInit(timeout: Long = 5L, unit: TimeUnit = TimeUnit.SECONDS) =
        Systrace.trace("async-init-wait") {
            asyncInitTask.get()?.get(timeout, unit)
        }
}
