package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.anr.ndk.EmbraceNativeThreadSamplerService
import io.embrace.android.embracesdk.internal.anr.ndk.NativeAnrOtelMapper
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerInstaller
import io.embrace.android.embracesdk.internal.anr.ndk.NativeThreadSamplerService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.ndk.NativeCrashDataSourceImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashHandlerInstaller
import io.embrace.android.embracesdk.internal.ndk.NativeCrashHandlerInstallerImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.ndk.NativeInstallMessage
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.Worker

internal class NativeFeatureModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    nativeCoreModule: NativeCoreModule,
) : NativeFeatureModule {

    override val nativeThreadSamplerService: NativeThreadSamplerService? by singleton {
        Systrace.traceSynchronous("native-thread-sampler-init") {
            if (nativeThreadSamplingEnabled(configModule.configService)) {
                EmbraceNativeThreadSamplerService(
                    configService = configModule.configService,
                    symbols = lazy { nativeCoreModule.symbolService.symbolsForCurrentArch },
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
                    sharedObjectLoader = nativeCoreModule.sharedObjectLoader
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
                nativeCrashProcessor = nativeCoreModule.processor,
                preferencesService = androidServicesModule.preferencesService,
                logWriter = essentialServiceModule.logWriter,
                configService = configModule.configService,
                serializer = initModule.jsonSerializer,
                logger = initModule.logger,
            )
        }
    }

    override val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller? by singleton {
        if (configModule.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            NativeCrashHandlerInstallerImpl(
                configService = configModule.configService,
                sharedObjectLoader = nativeCoreModule.sharedObjectLoader,
                logger = initModule.logger,
                delegate = nativeCoreModule.delegate,
                backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                nativeInstallMessage = nativeInstallMessage ?: return@singleton null,
                mainThreadHandler = AndroidMainThreadHandler()
            )
        } else {
            null
        }
    }

    private val nativeInstallMessage: NativeInstallMessage? by singleton {
        val reportBasePath = runCatching { storageModule.storageService.getOrCreateNativeCrashDir().absolutePath }
            .getOrNull() ?: return@singleton null
        val sessionId = essentialServiceModule.sessionIdTracker.getActiveSessionId() ?: return@singleton null
        val markerFilePath =
            storageModule.storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME).absolutePath
        NativeInstallMessage(
            reportPath = reportBasePath,
            markerFilePath = markerFilePath,
            sessionId = sessionId,
            appState = essentialServiceModule.processStateService.getAppState(),
            reportId = Uuid.getEmbUuid(),
            apiLevel = Build.VERSION.SDK_INT,
            is32bit = payloadSourceModule.deviceArchitecture.is32BitDevice,
            devLogging = false,
        )
    }

    private fun nativeThreadSamplingEnabled(configService: ConfigService) =
        configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()
}
