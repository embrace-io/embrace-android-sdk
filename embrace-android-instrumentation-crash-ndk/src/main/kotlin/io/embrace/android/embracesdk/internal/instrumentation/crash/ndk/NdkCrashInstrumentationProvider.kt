package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.handler.AndroidMainThreadHandler
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegate
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.internal.worker.Worker

var jniDelegateTestOverride: JniDelegate? = null
var sharedObjectLoaderTestOverride: SharedObjectLoader? = null

class NdkCrashInstrumentationProvider : InstrumentationProvider {

    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        if (!args.configService.autoDataCaptureBehavior.isNativeCrashCaptureEnabled()) {
            return null
        }
        return DataSourceState(
            factory = {
                val delegate = jniDelegateTestOverride ?: JniDelegateImpl()
                val sharedObjectLoader =
                    sharedObjectLoaderTestOverride ?: SharedObjectLoaderImpl(args.logger)
                val nativeOutputDir = StorageLocation.NATIVE.asFile(
                    logger = args.logger,
                    rootDirSupplier = { args.context.filesDir },
                    fallbackDirSupplier = { args.context.cacheDir }
                )

                val processor = NativeCrashProcessorImpl(
                    args,
                    sharedObjectLoader,
                    delegate,
                    args.configService.nativeSymbolMap,
                    nativeOutputDir,
                    args.priorityWorker(Worker.Priority.DataPersistenceWorker)
                )

                val nativeCrashHandlerInstaller = NativeCrashHandlerInstallerImpl(
                    args,
                    sharedObjectLoader = sharedObjectLoader,
                    delegate = delegate,
                    mainThreadHandler = AndroidMainThreadHandler(),
                    outputDir = nativeOutputDir,
                )
                nativeCrashHandlerInstaller.install()
                NativeCrashDataSourceImpl(
                    nativeCrashProcessor = processor,
                    args = args,
                )
            },
        )
    }

    // crashes are important and should be initialized before other instrumentation
    override val priority: Int = 700
}
