package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.anr.ndk.EmbraceNativeThreadSamplerService
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkService
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkServiceRepository
import io.embrace.android.embracesdk.internal.ndk.NdkDelegateImpl
import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class NativeModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    androidServicesModule: AndroidServicesModule,
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
                essentialServiceModule.sessionProperties,
                essentialServiceModule.sharedObjectLoader,
                initModule.logger,
                embraceNdkServiceRepository,
                NdkDelegateImpl(),
                workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
                workerThreadModule.backgroundWorker(WorkerName.SERVICE_INIT),
                essentialServiceModule.deviceArchitecture,
                initModule.jsonSerializer
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

    override val nativeAnrOtelMapper: NativeAnrOtelMapper by singleton {
        NativeAnrOtelMapper(nativeThreadSamplerService, initModule.jsonSerializer, initModule.clock)
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
