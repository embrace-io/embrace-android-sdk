package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.anr.ndk.EmbraceNativeThreadSamplerService
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkService
import io.embrace.android.embracesdk.internal.ndk.EmbraceNdkServiceRepository
import io.embrace.android.embracesdk.internal.ndk.NativeCrashDataSourceImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.ndk.NdkDelegateImpl
import io.embrace.android.embracesdk.internal.ndk.NdkService
import io.embrace.android.embracesdk.internal.worker.Worker

internal class NativeFeatureModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    nativeCoreModule: NativeCoreModule,
) : NativeFeatureModule {

    override val ndkService: NdkService by singleton {
        Systrace.traceSynchronous("ndk-service-init") {
            EmbraceNdkService(
                coreModule.context,
                storageModule.storageService,
                payloadSourceModule.metadataService,
                essentialServiceModule.processStateService,
                configModule.configService,
                essentialServiceModule.userService,
                essentialServiceModule.sessionPropertiesService,
                nativeCoreModule.sharedObjectLoader,
                initModule.logger,
                embraceNdkServiceRepository,
                NdkDelegateImpl(),
                workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                payloadSourceModule.deviceArchitecture,
                initModule.jsonSerializer
            )
        }
    }

    override val nativeThreadSamplerService: NativeThreadSamplerService? by singleton {
        Systrace.traceSynchronous("native-thread-sampler-init") {
            if (nativeThreadSamplingEnabled(configModule.configService)) {
                EmbraceNativeThreadSamplerService(
                    configService = configModule.configService,
                    symbols = lazy { ndkService.symbolsForCurrentArch },
                    logger = initModule.logger,
                    worker = workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                    deviceArchitecture = payloadSourceModule.deviceArchitecture,
                    sharedObjectLoader = nativeCoreModule.sharedObjectLoader,
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
            if (nativeThreadSamplingEnabled(configModule.configService)) {
                NativeThreadSamplerInstaller(
                    sharedObjectLoader = nativeCoreModule.sharedObjectLoader,
                    logger = initModule.logger
                )
            } else {
                null
            }
        }
    }

    override val nativeCrashService: NativeCrashService? by singleton {
        if (!configModule.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            null
        } else {
            NativeCrashDataSourceImpl(
                sessionPropertiesService = essentialServiceModule.sessionPropertiesService,
                ndkService = ndkService,
                preferencesService = androidServicesModule.preferencesService,
                logWriter = essentialServiceModule.logWriter,
                configService = configModule.configService,
                serializer = initModule.jsonSerializer,
                logger = initModule.logger,
            )
        }
    }

    private fun nativeThreadSamplingEnabled(configService: ConfigService) =
        configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()

    private val embraceNdkServiceRepository by singleton {
        EmbraceNdkServiceRepository(
            storageModule.storageService,
            initModule.logger
        )
    }
}
