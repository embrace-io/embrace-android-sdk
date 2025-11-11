package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import android.os.Build
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.injection.ConfigModule
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.StorageModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.asFile
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolServiceImpl
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.Uuid
import io.embrace.android.embracesdk.internal.worker.Worker

class NativeCoreModuleImpl(
    configModule: ConfigModule,
    workerThreadModule: WorkerThreadModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    args: InstrumentationArgs,
    otelModule: OpenTelemetryModule,
    delegateProvider: Provider<JniDelegate?>,
    sharedObjectLoaderProvider: Provider<SharedObjectLoader?>,
    symbolServiceProvider: Provider<SymbolService?>,
) : NativeCoreModule {

    override val delegate by lazy {
        delegateProvider() ?: JniDelegateImpl()
    }

    override val symbolService: SymbolService by lazy {
        symbolServiceProvider() ?: SymbolServiceImpl(
            configModule.cpuAbi,
            args.serializer,
            args.logger
        )
    }

    override val sharedObjectLoader: SharedObjectLoader by lazy {
        sharedObjectLoaderProvider() ?: SharedObjectLoaderImpl(args.logger)
    }

    private val nativeOutputDir by lazy { StorageLocation.NATIVE.asFile(args.context, args.logger) }

    override val processor: NativeCrashProcessor = NativeCrashProcessorImpl(
        sharedObjectLoader,
        args.logger,
        delegate,
        args.serializer,
        symbolService,
        nativeOutputDir,
        workerThreadModule.priorityWorker(Worker.Priority.DataPersistenceWorker)
    )

    override val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller? by lazy {
        if (args.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            NativeCrashHandlerInstallerImpl(
                sharedObjectLoader = sharedObjectLoader,
                delegate = delegate,
                nativeInstallMessage = nativeInstallMessage,
                mainThreadHandler = AndroidMainThreadHandler(),
                args = args,
                processIdProvider = { otelModule.otelSdkConfig.processIdentifier },
                outputDir = nativeOutputDir
            )
        } else {
            null
        }
    }

    private val nativeInstallMessage: NativeInstallMessage by lazy {
        val markerFilePath =
            storageModule.storageService.getFileForWrite("embrace_crash_marker").absolutePath
        NativeInstallMessage(
            markerFilePath = markerFilePath,
            appState = essentialServiceModule.appStateTracker.getAppState(),
            reportId = Uuid.getEmbUuid(),
            apiLevel = Build.VERSION.SDK_INT,
            is32bit = configModule.cpuAbi.is32BitDevice,
            devLogging = false,
        )
    }
}
