package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.injection.EssentialServiceModule
import io.embrace.android.embracesdk.internal.injection.OpenTelemetryModule
import io.embrace.android.embracesdk.internal.injection.StorageModule
import io.embrace.android.embracesdk.internal.injection.WorkerThreadModule
import io.embrace.android.embracesdk.internal.injection.asFile
import io.embrace.android.embracesdk.internal.injection.singleton
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolServiceImpl
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

class NativeCoreModuleImpl(
    workerThreadModule: WorkerThreadModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    args: InstrumentationArgs,
    otelModule: OpenTelemetryModule,
    delegateProvider: Provider<JniDelegate?>,
    sharedObjectLoaderProvider: Provider<SharedObjectLoader?>,
    symbolServiceProvider: Provider<SymbolService?>,
) : NativeCoreModule {

    override val delegate by singleton {
        delegateProvider() ?: JniDelegateImpl()
    }

    override val symbolService: SymbolService by singleton {
        symbolServiceProvider() ?: SymbolServiceImpl(
            args.cpuAbi,
            args.serializer,
            args.logger
        )
    }

    override val sharedObjectLoader: SharedObjectLoader by singleton {
        sharedObjectLoaderProvider() ?: SharedObjectLoaderImpl(args.logger)
    }

    private val nativeOutputDir by lazy { StorageLocation.NATIVE.asFile(args.context, args.logger) }

    override val processor: NativeCrashProcessor = NativeCrashProcessorImpl(
        args,
        sharedObjectLoader,
        delegate,
        symbolService,
        nativeOutputDir,
        workerThreadModule.priorityWorker(Worker.Priority.DataPersistenceWorker)
    )

    override val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller? by singleton {
        if (args.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            val markerFilePath =
                storageModule.storageService.getFileForWrite("embrace_crash_marker").absolutePath
            NativeCrashHandlerInstallerImpl(
                args,
                sharedObjectLoader = sharedObjectLoader,
                delegate = delegate,
                mainThreadHandler = AndroidMainThreadHandler(),
                sessionIdTracker = essentialServiceModule.sessionIdTracker,
                processIdProvider = { otelModule.otelSdkConfig.processIdentifier },
                outputDir = nativeOutputDir,
                markerFilePath = markerFilePath,
            )
        } else {
            null
        }
    }
}
