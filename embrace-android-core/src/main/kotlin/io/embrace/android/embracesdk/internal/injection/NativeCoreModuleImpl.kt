package io.embrace.android.embracesdk.internal.injection

import android.os.Build
import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.SharedObjectLoaderImpl
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.ndk.NativeCrashHandlerInstaller
import io.embrace.android.embracesdk.internal.ndk.NativeCrashHandlerInstallerImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashProcessor
import io.embrace.android.embracesdk.internal.ndk.NativeCrashProcessorImpl
import io.embrace.android.embracesdk.internal.ndk.NativeInstallMessage
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolServiceImpl
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.Worker

internal class NativeCoreModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    payloadSourceModule: PayloadSourceModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    otelModule: OpenTelemetryModule,
) : NativeCoreModule {

    override val delegate by singleton {
        JniDelegateImpl()
    }

    override val symbolService: SymbolService = SymbolServiceImpl(
        coreModule.context,
        payloadSourceModule.deviceArchitecture,
        initModule.jsonSerializer,
        initModule.logger
    )

    override val sharedObjectLoader: SharedObjectLoader by singleton {
        SharedObjectLoaderImpl(initModule.logger)
    }

    private val nativeOutputDir by lazy { StorageLocation.NATIVE.asFile(coreModule.context, initModule.logger) }

    override val processor: NativeCrashProcessor = NativeCrashProcessorImpl(
        sharedObjectLoader,
        initModule.logger,
        delegate,
        initModule.jsonSerializer,
        symbolService,
        nativeOutputDir,
        workerThreadModule.priorityWorker(Worker.Priority.DataPersistenceWorker)
    )

    override val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller? by singleton {
        if (configModule.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            NativeCrashHandlerInstallerImpl(
                configService = configModule.configService,
                sharedObjectLoader = sharedObjectLoader,
                logger = initModule.logger,
                delegate = delegate,
                backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                nativeInstallMessage = nativeInstallMessage ?: return@singleton null,
                mainThreadHandler = AndroidMainThreadHandler(),
                clock = initModule.clock,
                sessionIdTracker = essentialServiceModule.sessionIdTracker,
                processIdProvider = { otelModule.openTelemetryConfiguration.processIdentifier },
                outputDir = nativeOutputDir
            )
        } else {
            null
        }
    }

    private val nativeInstallMessage: NativeInstallMessage? by singleton {
        val markerFilePath =
            storageModule.storageService.getFileForWrite("embrace_crash_marker").absolutePath
        NativeInstallMessage(
            markerFilePath = markerFilePath,
            appState = essentialServiceModule.processStateService.getAppState(),
            reportId = Uuid.getEmbUuid(),
            apiLevel = Build.VERSION.SDK_INT,
            is32bit = payloadSourceModule.deviceArchitecture.is32BitDevice,
            devLogging = false,
        )
    }
}
