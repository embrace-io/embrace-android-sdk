package io.embrace.android.embracesdk.ndk

import io.embrace.android.embracesdk.anr.ndk.EmbraceNativeThreadSamplerService
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.injection.AndroidServicesModule
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.DeliveryModule
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.InitModule
import io.embrace.android.embracesdk.injection.StorageModule
import io.embrace.android.embracesdk.injection.singleton
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface NativeModule {
    val ndkService: NdkService
    val nativeThreadSamplerService: NativeThreadSamplerService?
    val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller?
}

internal class NativeModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    androidServicesModule: AndroidServicesModule,
    sessionProperties: EmbraceSessionProperties,
    workerThreadModule: WorkerThreadModule
) : NativeModule {

    override val ndkService: NdkService by singleton {
        Systrace.traceSynchronous("ndk-service-init") {
            EmbraceNdkService(
                coreModule.context,
                storageModule.storageService,
                essentialServiceModule.metadataService,
                essentialServiceModule.processStateService,
                essentialServiceModule.configService,
                deliveryModule.deliveryService,
                essentialServiceModule.userService,
                androidServicesModule.preferencesService,
                sessionProperties,
                coreModule.appFramework,
                essentialServiceModule.sharedObjectLoader,
                initModule.logger,
                embraceNdkServiceRepository,
                NdkDelegateImpl(),
                workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                workerThreadModule.backgroundWorker(WorkerName.SERVICE_INIT),
                essentialServiceModule.deviceArchitecture,
                coreModule.jsonSerializer
            )
        }
    }

    override val nativeThreadSamplerService: NativeThreadSamplerService? by singleton {
        Systrace.traceSynchronous("native-thread-sampler-init") {
            if (nativeThreadSamplingEnabled(essentialServiceModule.configService)) {
                EmbraceNativeThreadSamplerService(
                    configService = essentialServiceModule.configService,
                    symbols = lazy { ndkService.getSymbolsForCurrentArch() },
                    logger = initModule.logger,
                    scheduledWorker = workerThreadModule.scheduledWorker(WorkerName.BACKGROUND_REGISTRATION),
                    deviceArchitecture = essentialServiceModule.deviceArchitecture,
                    sharedObjectLoader = essentialServiceModule.sharedObjectLoader,
                )
            } else {
                null
            }
        }
    }

    override val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller? by singleton {
        Systrace.traceSynchronous("native-thread-sampler-installer-init") {
            if (nativeThreadSamplingEnabled(essentialServiceModule.configService)) {
                NativeThreadSamplerInstaller(
                    sharedObjectLoader = essentialServiceModule.sharedObjectLoader,
                    logger = initModule.logger
                )
            } else {
                null
            }
        }
    }

    private fun nativeThreadSamplingEnabled(configService: ConfigService) = configService.autoDataCaptureBehavior.isNdkEnabled()

    private val embraceNdkServiceRepository by singleton {
        EmbraceNdkServiceRepository(
            storageModule.storageService,
            initModule.logger
        )
    }
}
