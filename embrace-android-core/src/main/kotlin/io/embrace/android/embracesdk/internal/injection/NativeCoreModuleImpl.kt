package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import io.embrace.android.embracesdk.internal.SharedObjectLoaderImpl
import io.embrace.android.embracesdk.internal.ndk.NativeCrashProcessor
import io.embrace.android.embracesdk.internal.ndk.NativeCrashProcessorImpl
import io.embrace.android.embracesdk.internal.ndk.jni.JniDelegateImpl
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolService
import io.embrace.android.embracesdk.internal.ndk.symbols.SymbolServiceImpl

internal class NativeCoreModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    payloadSourceModule: PayloadSourceModule,
    storageModule: StorageModule,
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

    override val processor: NativeCrashProcessor = NativeCrashProcessorImpl(
        sharedObjectLoader,
        initModule.logger,
        delegate,
        initModule.jsonSerializer,
        symbolService,
        storageModule.storageService,
    )
}
