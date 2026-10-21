package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.internal.worker.Worker

var jniDelegateTestOverride: JniDelegate? = null
var sharedObjectLoaderTestOverride: SharedObjectLoader? = null

class NdkCrashInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        if (!args.configService.config.autoDataCapture.nativeCrashCaptureEnabled) {
            return null
        }
        return {
            val delegate = jniDelegateTestOverride ?: JniDelegateImpl()
            val sharedObjectLoader =
                sharedObjectLoaderTestOverride ?: SharedObjectLoaderImpl(args.errorHandler)
            val nativeOutputDir = StorageLocation.NATIVE.asFile(
                errorHandler = args.errorHandler,
                rootDirSupplier = { args.context.filesDir },
                fallbackDirSupplier = { args.context.cacheDir },
            )

            val processor = NativeCrashProcessorImpl(
                args,
                sharedObjectLoader,
                delegate,
                args.configService.nativeSymbolMap,
                nativeOutputDir,
                args.priorityWorker(Worker.Priority.DataPersistenceWorker),
            )

            val nativeCrashHandlerInstaller = NativeCrashHandlerInstallerImpl(
                args,
                sharedObjectLoader = sharedObjectLoader,
                delegate = delegate,
                mainThreadHandler = AndroidMainThreadHandler(args.errorHandler),
                outputDir = nativeOutputDir,
            )
            nativeCrashHandlerInstaller.install()
            NativeCrashDataSourceImpl(
                nativeCrashProcessor = processor,
                nativeCrashHandlerInstaller = nativeCrashHandlerInstaller,
                args = args,
            )
        }
    }

    // crashes are important and should be initialized before other instrumentation
    override val priority: Int = 700
}
