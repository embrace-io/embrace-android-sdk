package io.embrace.android.embracesdk.ndk

import io.embrace.android.embracesdk.anr.ndk.EmbraceNativeThreadSamplerService
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.DeliveryModule
import io.embrace.android.embracesdk.injection.EssentialServiceModule
import io.embrace.android.embracesdk.injection.StorageModule
import io.embrace.android.embracesdk.injection.singleton
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface NativeModule {
    val ndkService: NdkService
    val nativeThreadSamplerService: NativeThreadSamplerService?
    val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller?
}

internal class NativeModuleImpl(
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    sessionProperties: EmbraceSessionProperties,
    workerThreadModule: WorkerThreadModule
) : NativeModule {

    override val ndkService: NdkService by singleton {
        EmbraceNdkService(
            coreModule.context,
            storageModule.storageManager,
            essentialServiceModule.metadataService,
            essentialServiceModule.processStateService,
            essentialServiceModule.configService,
            deliveryModule.deliveryService,
            essentialServiceModule.userService,
            sessionProperties,
            coreModule.appFramework,
            essentialServiceModule.sharedObjectLoader,
            coreModule.logger,
            embraceNdkServiceRepository,
            NdkDelegateImpl(),
            workerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION),
            workerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION),
            essentialServiceModule.deviceArchitecture,
            coreModule.jsonSerializer
        )
    }

    override val nativeThreadSamplerService: NativeThreadSamplerService? by singleton {
        if (nativeThreadSamplingEnabled(essentialServiceModule.configService, essentialServiceModule.sharedObjectLoader)) {
            EmbraceNativeThreadSamplerService(
                essentialServiceModule.configService,
                lazy { ndkService.getSymbolsForCurrentArch() },
                executorService = workerThreadModule.scheduledExecutor(ExecutorName.BACKGROUND_REGISTRATION),
                deviceArchitecture = essentialServiceModule.deviceArchitecture
            )
        } else {
            null
        }
    }

    override val nativeThreadSamplerInstaller: NativeThreadSamplerInstaller? by singleton {
        if (nativeThreadSamplingEnabled(essentialServiceModule.configService, essentialServiceModule.sharedObjectLoader)) {
            NativeThreadSamplerInstaller()
        } else {
            null
        }
    }

    private fun nativeThreadSamplingEnabled(configService: ConfigService, sharedObjectLoader: SharedObjectLoader) =
        configService.autoDataCaptureBehavior.isNdkEnabled() && sharedObjectLoader.loadEmbraceNative()

    private val embraceNdkServiceRepository by singleton {
        EmbraceNdkServiceRepository(
            storageModule.storageManager,
            coreModule.logger
        )
    }
}
