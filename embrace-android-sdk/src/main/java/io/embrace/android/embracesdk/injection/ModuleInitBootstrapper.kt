package io.embrace.android.embracesdk.injection

import android.content.Context
import io.embrace.android.embracesdk.Embrace.AppFramework
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.internal.utils.AndroidServicesModuleSupplier
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.CoreModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.DeliveryModuleSupplier
import io.embrace.android.embracesdk.internal.utils.EssentialServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.StorageModuleSupplier
import io.embrace.android.embracesdk.internal.utils.SystemServiceModuleSupplier
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.utils.WorkerThreadModuleSupplier
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl
import java.util.concurrent.atomic.AtomicBoolean

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

    private val initialized = AtomicBoolean(false)

    @JvmOverloads
    fun init(
        context: Context,
        enableIntegrationTesting: Boolean,
        appFramework: AppFramework,
        customAppId: String? = null,
        configServiceProvider: () -> ConfigService? = { null },
        versionChecker: VersionChecker = BuildVersionChecker,
    ): Boolean {
        if (initialized.get()) {
            return false
        }

        synchronized(initialized) {
            return if (!initialized.get()) {
                coreModule = coreModuleSupplier(context, appFramework)
                workerThreadModule = workerThreadModuleSupplier(initModule)
                systemServiceModule = systemServiceModuleSupplier(coreModule, versionChecker)
                androidServicesModule = androidServicesModuleSupplier(initModule, coreModule, workerThreadModule)
                storageModule = storageModuleSupplier(initModule, coreModule, workerThreadModule)
                essentialServiceModule =
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
                dataCaptureServiceModule =
                    dataCaptureServiceModuleSupplier(
                        initModule,
                        openTelemetryModule,
                        coreModule,
                        systemServiceModule,
                        essentialServiceModule,
                        workerThreadModule,
                        versionChecker
                    )
                deliveryModule = deliveryModuleSupplier(coreModule, workerThreadModule, storageModule, essentialServiceModule)
                initialized.set(true)
                true
            } else {
                false
            }
        }
    }
}
