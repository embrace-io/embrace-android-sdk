package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.symbols.SymbolServiceImpl
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

class NativeCoreModuleImpl(
    args: InstrumentationArgs,
    delegateProvider: Provider<JniDelegate?>,
    sharedObjectLoaderProvider: Provider<SharedObjectLoader?>,
    symbolServiceProvider: Provider<SymbolService?>,
) : NativeCoreModule {

    override val delegate by lazy {
        delegateProvider() ?: JniDelegateImpl()
    }

    override val symbolService: SymbolService by lazy {
        symbolServiceProvider() ?: SymbolServiceImpl(
            args.cpuAbi,
            args.serializer,
            args.logger
        )
    }

    override val sharedObjectLoader: SharedObjectLoader by lazy {
        sharedObjectLoaderProvider() ?: SharedObjectLoaderImpl(args.logger)
    }

    private val nativeOutputDir = StorageLocation.NATIVE.asFile(
        logger = args.logger,
        rootDirSupplier = { args.context.filesDir },
        fallbackDirSupplier = { args.context.cacheDir }
    )

    override val processor: NativeCrashProcessor = NativeCrashProcessorImpl(
        args,
        sharedObjectLoader,
        delegate,
        symbolService,
        nativeOutputDir,
        args.priorityWorker(Worker.Priority.DataPersistenceWorker)
    )

    override val nativeCrashHandlerInstaller: NativeCrashHandlerInstaller? by lazy {
        if (args.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            NativeCrashHandlerInstallerImpl(
                args,
                sharedObjectLoader = sharedObjectLoader,
                delegate = delegate,
                mainThreadHandler = AndroidMainThreadHandler(),
                outputDir = nativeOutputDir,
            )
        } else {
            null
        }
    }

    override val nativeCrashService: NativeCrashService? by lazy {
        if (!args.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            null
        } else {
            NativeCrashDataSourceImpl(
                nativeCrashProcessor = processor,
                args = args,
            )
        }
    }
}
